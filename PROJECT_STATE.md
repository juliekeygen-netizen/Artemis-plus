# Artemis Plus — Durable Project State and Roadmap

**Last refreshed:** 2026-09-02  
**Purpose:** durable current-state handoff for Codex/ChatGPT and future contributors.  
**Rule:** current source/tests, Git history, Actions, and release state are authoritative. Verify live `main` before acting; this file is a recovery map, not a substitute for inspection.

---

## 1. Project direction

Artemis Plus is an Android streaming client derived from the newer Marssvoodoo Artemis base. The project keeps that streaming/reliability base while adding Artemis-specific control editing, profiles, Quick Menu actions, OSC management, orientation/PiP/background-stream behavior, experimental sideways streaming, and a stable signed rolling debug release.

Primary architectural rule: extend the existing Artemis ownership model rather than creating parallel state systems. Prefer narrow, tested fixes over broad rewrites.

---

## 2. Current verified baseline

Latest verified merged `main` at this refresh:

`e7b338638c069e1648d40e579e19be81bdc323ae`

Commit:

`fix: make settings profile storage atomic (#38)`

Post-merge verification on this exact SHA:

- Android CI run #289 (`33637261255`) — success;
- Build Debug APK run #125 (`33637261090`) — success.

Current Android build stack observed in the repository:

- Android Gradle Plugin: **8.13.0**;
- Gradle wrapper: **8.14.2**;
- Java: **17**;
- NDK: **27.0.12077973**.

The debuggable+minified build warning is currently informational by design; do not disable shrinking merely to silence it without measuring the intended build behavior.

---

## 3. Important merged audit work after the 2026-09-01 state sync

Do not restart these from stale handoffs.

### Release / workflow / build maintenance

- **#20** `9de0df8c…` — hardened rolling release signing identity/workflow, main-only privileged publication, signer verification, safer rolling-release updates, and local signing fail-closed behavior.
- **#21** `90189675…` — fixed live `apksigner` SHA-256 parsing.
- **#22** `0cd8ca73…` — fixture-tested fail-closed signer digest extraction.
- **#27** `0ad4292c…` — moved GitHub-hosted actions to Node-24-capable majors while pinning the proven Android command-line tools/runtime assumptions.
- **#28** `2c8c494c…` — removed stale localized Performance Charts resources and fixed static percent-bearing resource warnings without line-ending churn.
- **#34** `794398a6…` — replaced the seven repository-owned deprecated Gradle Groovy property setter forms with assignment syntax. Full warning audit afterward showed those Gradle deprecations gone.

Established APK signing certificate SHA-256 remains:

`88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

Never regenerate/replace the signing identity casually and never commit signing secrets.

### Quick Menu / OSC / keyboard persistence

- **#23** `423eb27b…` — preserves unknown/future Quick Menu action IDs inertly and round-trippably.
- **#25** `4228653a…` — recovers OSC profiles from damaged metadata while preserving valid siblings and avoiding stale duplicate resurrection.
- **#29** `2ad7ae35…` — hardens keyboard/profile persistence against wrong-typed and partially malformed state while preserving valid siblings and unknown future fields.

### Editor geometry / floating controls / settings corruption recovery

- **#30** `9fc934f6…` — **completed the connected long-label expansion fix** using pre-expansion graph topology. It translates the complete connected follower component, including offset descendants, while leaving left/below-only branches fixed. Do not reimplement this from older notes.
- **#32** `bf7635a0…` — rejects non-finite floating-control coordinates and recovers wrong-typed stored positions instead of crashing.
- **#33** `4560b76d…` — recovers malformed outside-stream orientation preference to Follow System.
- **#35** `8caa1f1d…` — recovers malformed floating-control reset-between-sessions preference without discarding valid saved positions.

### Settings profile recovery

- **#37** `838edcb7…` — normalizes Gson-restored string sets and makes typed profile/base preference reads fall back safely when persisted values have stale/wrong types.
- **#38** `e7b33863…` — stores `profiles.json` through Android `AtomicFile`, serializes file access, validates a complete deserialized map before replacing live state, clears dangling active profile IDs, and adds interrupted-write/malformed-commit regressions.

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

## 5. Current audit conclusions and remaining work

### 5.1 Gradle deprecation audit is resolved

The repository-owned Gradle property assignment warnings found under `--warning-mode all` were fixed in #34. Do not perform a Gradle/AGP upgrade just because the older handoff mentioned deprecations.

### 5.2 Settings/profile persistence is materially hardened, but audit continues

Recent work now covers:

- keyboard/profile malformed-state recovery;
- OSC damaged metadata recovery;
- floating-control malformed state;
- outside-stream orientation malformed state;
- profile overlay wrong-type recovery;
- atomic settings-profile file writes and transactional reload.

Continue auditing remaining persisted owners rather than redoing these paths. Priority candidates include:

- `QuickMenuConfig` storage and malformed-version recovery;
- `KeyComboManager` and other JSON/SharedPreferences stores;
- active-reference repair and duplicate/stale ID handling;
- apply-order/listener races;
- any direct non-atomic file stores still carrying user state.

### 5.3 Background streaming implementation is already substantial

Current `Game` code includes Keep Alive and Fast Resume lifecycle ownership, foreground keep-alive service handling, headless output switching, wake-lock timeout behavior, PiP exclusions, and fallback logic. Treat this as an audit surface, not an unimplemented feature.

Still prioritize regressions for:

- background park/resume and reconnect failure;
- delayed Keep Alive teardown ownership;
- Surface/TextureView restoration ordering;
- controller detach/reconcile while parked;
- Activity destruction while delayed lifecycle callbacks remain queued.

### 5.4 Concrete PiP reliability gap found on Android 11

The manual PiP path is version-split correctly:

- Android O–Q: `onUserLeaveHint()` manually enters PiP;
- Android R: `onPictureInPictureRequested()` manually enters PiP;
- Android S+: system auto-enter is used when enabled.

The O–Q path already catches OEM exceptions because manual `enterPictureInPictureMode()` has historically thrown on some devices. The Android R callback currently performs the same manual entry **without** that guard. A narrow next patch should make Android R manual entry fail gracefully rather than crash, while preserving callback semantics and the Android S+ auto-enter path.

Prefer a small testable helper/entry boundary instead of a broad `Game` lifecycle rewrite.

### 5.5 Localization remains incomplete

Editor shell localization improved earlier, but display metadata such as Artemis Action / Quick Menu registry labels/categories/descriptions still contains hard-coded English. Stable persisted/runtime IDs must never be translated; resource-back display metadata only.

### 5.6 Sideways/foldable behavior remains hardware-sensitive

Robolectric/emulator success does not prove OEM MediaCodec/TextureView/orientation/PiP/foldable behavior. Real-device validation remains necessary for:

- MediaCodec output-surface switching;
- TextureView restoration timing;
- Keep Alive headless-output transitions;
- PiP enter/exit appearance;
- CW/CCW input transforms;
- IME/system-window behavior;
- foldable cover/posture behavior.

The Diana audit did not find a complete reusable cover-screen controller/analog-trigger subsystem to port wholesale; future foldable work should be capability-gated.

---

## 6. Next audit priorities

Keep unrelated fixes in separate coherent PRs.

1. **Android 11 PiP manual-entry exception hardening** with focused regression coverage.
2. **Remaining persisted-state robustness**: Quick Menu, key combos, other state owners, listener/apply ordering, stale references, direct file writes.
3. **Lifecycle/race coverage**: background park/resume, Keep Alive fallback/teardown, surface restoration, controller detach, delayed callbacks after teardown.
4. **Repository/security hygiene**: generated binaries, local configs, secrets, temporary diagnostics, unrelated helper artifacts.
5. **UI/localization debt** once correctness work is exhausted.
6. **Useful contained feature work** only after the current correctness/lifecycle queue is exhausted.

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
