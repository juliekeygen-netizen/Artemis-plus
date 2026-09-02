# Artemis Plus — Durable Project State and Roadmap

**Last refreshed:** 2026-09-02  
**Purpose:** durable current-state handoff for Codex/ChatGPT and future contributors.  
**Rule:** current source/tests, Git history, Actions, and release state are authoritative. Verify live `main` before acting; this file is a recovery map, not a substitute for inspection.

---

## 1. Project direction

Artemis Plus is an Android streaming client derived from the newer Marssvoodoo Artemis base. The project keeps that streaming/reliability base while adding Artemis-specific control editing, profiles, Quick Menu actions, OSC management, orientation/PiP/background-stream behavior, experimental sideways streaming, and a stable signed rolling debug release.

Primary architectural rule: extend the existing Artemis ownership model rather than creating parallel state systems. Prefer narrow, demonstrated, regression-tested fixes over broad rewrites.

---

## 2. Current verified baseline

Latest verified merged `main` at this refresh:

`47c58451186d8c6b55811d9f7a193a740e996f08`

Latest merge:

`Reject keyboard profiles that alias the same storage (#51)`

Post-merge verification on this exact SHA:

- Android CI run `33676129443` — success.

The #51 branch and PR also passed exact-head compile, focused Artemis regressions, and the full inherited unit suite before merge.

Current Android build stack:

- Android Gradle Plugin: **8.13.0**;
- Gradle wrapper: **8.14.2**;
- Java: **17**;
- NDK: **27.0.12077973**;
- Android platform: **36**.

Established APK signing certificate SHA-256:

`88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

Never regenerate/replace the signing identity casually and never commit signing secrets. The debuggable+minified warning is informational by design; do not disable shrinking merely to silence it.

---

## 3. Important merged audit work

Do not restart these from stale handoffs.

### Release / workflow / build maintenance

- **#20–#22** — hardened rolling-release signing identity/workflow, main-only privileged publication, signer verification, safer rolling-release updates, local signing fail-closed behavior, and fixture-tested `apksigner` SHA-256 parsing.
- **#27** — moved GitHub-hosted actions to Node-24-capable majors while preserving the proven Android toolchain assumptions.
- **#28** — cleaned stale resource/localization warnings without line-ending churn.
- **#34** — replaced repository-owned deprecated Gradle Groovy property setter forms with assignment syntax and re-audited warnings.

### Quick Menu / OSC / keyboard persistence

- **#23** — preserves unknown/future Quick Menu action IDs inertly and round-trippably.
- **#25** — recovers OSC profiles from damaged metadata while preserving valid siblings and avoiding stale duplicate resurrection.
- **#29** — hardens keyboard/profile persistence against wrong-typed and partially malformed state while preserving valid siblings and unknown future fields.
- **#40** — makes Quick Menu persisted preference reads tolerant of stale/wrong stored types.
- **#51** — rejects keyboard profile metadata entries that alias the same backing SharedPreferences storage. The first valid storage owner survives, backing layout data is preserved, and the active profile is repaired through the existing recovery path.

### Editor geometry / floating controls / orientation

- **#30** — completed connected long-label expansion using pre-expansion graph topology. The full connected follower component, including offset descendants, moves with expansion while left/below-only branches remain fixed.
- **#32** — rejects non-finite floating-control coordinates and recovers wrong-typed stored positions.
- **#33** — recovers malformed outside-stream orientation preference to Follow System.
- **#35** — recovers malformed floating-control reset-between-sessions state without discarding valid saved positions.

### Settings/profile persistence and Settings UI

- **#37** — normalizes Gson-restored string sets and makes typed profile/base preference reads fall back safely when persisted values have stale/wrong types.
- **#38** — stores `profiles.json` through Android `AtomicFile`, serializes file access, validates a complete deserialized map before replacing live state, clears dangling active profile IDs, and adds interrupted-write/malformed-commit regressions.
- **#42** — protects base/default preference reads even when no settings profile is active.
- **#43** — recovers malformed string-backed preferences instead of propagating wrong stored types.
- **#44** — makes settings-profile listener dispatch mutation-safe.
- **#45** — rejects duplicate settings-profile UUIDs transactionally during recovery instead of silently overwriting an earlier profile.
- **#48** — fixes profile-editor Gson boundary mismatches: JSON numbers restored as `Double`, string sets restored as `List`, and rotation/saved-state preservation for these values.
- **#49** — hardens the separate named `GlPreferences` store against wrong-typed `Renderer`/`Fingerprint` values and verifies later writes repair them.
- **#50** — routes normal/global Settings through a recovering base SharedPreferences/`PreferenceDataStore` adapter before pre-reads and XML inflation, while preserving the profile editor's in-memory data-store isolation.

### PiP / lifecycle correctness

- **#41** — guards Android 11 manual PiP entry so OEM/runtime exceptions do not crash the stream Activity while preserving the O–Q manual and Android 12+ auto-enter version split.

---

## 4. Major product systems already present

### Editor / keyboard / Artemis Actions

- UI Editor V4 gesture/persistence hardening.
- Named custom key/chord buttons.
- Local Artemis Action buttons with direct-press semantics.
- Managed keyboard profiles: create/select/rename/duplicate/delete/reorder.
- Modern keyboard-profile bundle export/import including layout, custom keys, and Action selections.
- Unknown/future Action-ID preservation.
- Deterministic snapping and connected-label expansion behavior.

### OSC

- Managed gamepad OSC profiles.
- Automatic per-game selection using stable host/app identity.
- Corruption recovery from active references, per-game mappings, and initialized snapshots.

### Quick Menu

- Versioned persisted nested hierarchy.
- Stable action registry IDs.
- Nested pages, Back navigation, reorder/remove/rename/reset.
- Search/category action picker.
- Runtime-resolved ephemeral actions instead of persisting device/session-specific entries.
- Parser/editor depth/node limits and malformed-config fallback.
- Unknown future action IDs preserved inertly.

### Orientation / PiP / background lifecycle

- Separate outside-stream orientation policy.
- Settings search over the runtime preference tree.
- Bottom-edge Start gesture with buffered/replayed ordinary touch handling.
- PiP parameter/aspect-ratio and overlay restoration hardening.
- Fast Resume retained-session background mode.
- Experimental Keep Connection Alive using a headless decoder Surface where supported, with foreground service/readiness checks, wake-lock timeout handling, and Fast Resume fallback.
- `Game` no longer relies on conflicting `android:noHistory="true"` behavior.
- Experimental Sideways CW / CCW stream layout while keeping the Activity physically portrait.

---

## 5. Current audit conclusions

### 5.1 Persisted-state sweep is substantially hardened

The current audit has covered the principal Artemis-owned persistence boundaries: settings profiles/default overlays, profile-editor serialization, Quick Menu, OSC metadata, keyboard profile/key/action metadata, GL preferences, floating controls, and outside-stream orientation.

The post-#51 focused sweep found no additional reproducible corruption defect in `ArtemisActionButtonFactory` or the remaining independent Artemis preference owners. Action selections already use the keyboard-safe raw-map helpers, malformed saved button geometry is discarded, and the top-level action state reader is runtime-only.

Do not manufacture a broad persistence refactor merely because old handoffs still list these areas as unaudited. Re-open a persistence path only when a concrete invariant/failure is demonstrated.

### 5.2 Deliberately unresolved keyboard recovery edge

If the entire keyboard-profile metadata blob is destroyed, inactive **geometry-only** dynamic backing stores can become unreachable. The active dynamic store is preserved, and key/action metadata can identify some inactive stores, but there is no deterministic ownership signal for geometry-only stores.

Do **not** add a heuristic SharedPreferences scan that may resurrect cleared, orphaned, partial-import, or unrelated stores. A recovery patch needs an authoritative ownership marker or migration scheme first.

### 5.3 Background streaming implementation is already substantial

Current `Game` code includes Keep Alive and Fast Resume lifecycle ownership, foreground keep-alive service handling, headless output switching, wake-lock timeout behavior, PiP exclusions, fallback logic, and visible-surface restoration. Treat this as an audit/test surface, not an unimplemented feature.

High-value remaining lifecycle work is evidence-driven regression coverage around:

- background park/resume and reconnect failure;
- delayed Keep Alive teardown ownership;
- Surface/TextureView restoration ordering;
- controller detach/reconcile while parked;
- Activity destruction while delayed lifecycle callbacks remain queued.

### 5.4 PiP version split is now guarded

Android O–Q manual entry, Android R manual callback entry, and Android S+ system auto-enter remain intentionally distinct. The Android 11 manual exception gap identified in the previous documentation was fixed in #41. Older notes calling this the next patch are stale.

### 5.5 Localization remains incomplete

Display metadata such as Artemis Action / Quick Menu registry labels, categories, and descriptions still contains hard-coded English. Stable persisted/runtime IDs must never be translated; resource-back display metadata only.

### 5.6 Hardware-sensitive behavior still needs physical validation

Robolectric/emulator success does not prove OEM MediaCodec/TextureView/orientation/PiP/foldable behavior. Real-device validation remains important for:

- MediaCodec output-surface switching;
- TextureView restoration timing;
- Keep Alive headless-output transitions;
- PiP enter/exit appearance;
- Sideways CW/CCW video and input transforms;
- IME/system-window behavior;
- foldable cover/posture behavior.

The Diana audit did not find a complete reusable cover-screen controller/analog-trigger subsystem to port wholesale; future foldable work should be capability-gated.

---

## 6. Next audit priorities

Keep unrelated fixes in separate coherent PRs.

1. **Lifecycle/race coverage** — background park/resume, Keep Alive fallback/teardown, surface restoration, controller detach, and delayed callbacks after teardown.
2. **Repository/security hygiene** — generated binaries, machine-local config/path artifacts, secrets/signing material, temporary diagnostics, and unrelated helper artifacts.
3. **UI/localization debt** — resource-back user-facing Artemis Action / Quick Menu display metadata while preserving stable IDs.
4. **Performance/ownership review** — only where profiling or a concrete lifecycle/state invariant identifies a problem.
5. **Useful contained feature work** after correctness/lifecycle work is exhausted.

Persisted-state work should no longer be treated as a generic open-ended priority; revisit specific owners only when new evidence warrants it.

---

## 7. Real-device acceptance checklist for risky areas

When a physical-device pass is available, prioritize:

- normal stream start/stop/reconnect;
- Fast Resume within/after timeout;
- Keep Alive supported and fallback paths;
- background → foreground with controller connected/disconnected;
- Android 11 manual PiP enter failure/success and Android 12+ auto-enter;
- PiP exit and overlay restoration;
- Sideways CW/CCW video + touch + custom controls;
- custom-key editor drag/resize/snap and connected long-label growth;
- profile export/import and profile corruption/recovery;
- Quick Menu nested pages/reset;
- OSC per-game mapping/stale repair/profile deletion;
- outside-stream orientation on the target OEM device.

Do not classify input failure as a client regression until Apollo per-client input/launch permissions are checked.

---

## 8. Durable source-of-truth hierarchy

1. **Current source/tests + Git/Actions/Release history** — authoritative.
2. **`PROJECT_STATE.md`** — current roadmap/recovery map.
3. **`CODEX_HANDOFF.md`** — rolling latest coherent task/review packet.
4. **`AGENTS.md`** — stable operating/safety rules.
5. **`README.md` / `SIGNING.md`** — user-facing/release documentation.
6. **Old uploaded handoffs/chats** — historical intent only.

If these disagree, inspect current source/history and update stale documentation rather than coding from stale prose.
