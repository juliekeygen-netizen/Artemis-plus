# Artemis Plus — Durable Project State and Roadmap

**Last refreshed:** 2026-09-07
**Purpose:** durable current-state handoff for Codex/ChatGPT and future contributors.  
**Rule:** current source/tests, Git history, Actions, and release state are authoritative. Verify live `main` before acting; this file is a recovery map, not a substitute for inspection.

---

## 1. Project direction

Artemis Plus is an Android streaming client derived from the newer Marssvoodoo Artemis base. The project keeps that streaming/reliability base while adding Artemis-specific control editing, profiles, Quick Menu actions, OSC management, orientation/PiP/background-stream behavior, experimental sideways streaming, and a stable signed rolling debug release.

Primary architectural rule: extend existing ownership/state systems rather than creating parallel ones. Prefer narrow, demonstrated, regression-tested fixes over broad rewrites.

---

## 2. Current verified baseline

Latest verified product-code baseline at this refresh:

`28fbee2b81229e8696dc2451e81eaaf4f5e9b4d8`

Latest merge:

`Localize Artemis action catalog metadata (#77)`

Documentation-only PR [#78](https://github.com/juliekeygen-netizen/Artemis-plus/pull/78) refreshes the rolling state after #77; its merge does not change the product-code baseline above.

Post-merge verification on this exact SHA:

- Android CI run `34059021337` — success, including compile, the mandatory focused regression gate, and diagnostic full-suite reporting.
- Build Debug APK run `34059021340` — success, including established-signer verification, four-ABI package build, and rolling `debug-latest` publication.

PR #77 passed independent exact-head push CI `34045903759` and pull-request CI `34045905908` before its guarded squash merge. The audit added `ArtemisCatalogLocalizationTest` to the mandatory focused CI gate before merge.

Current Android build stack:

- Android Gradle Plugin: **8.13.0**;
- Gradle wrapper: **8.13**;
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
- **#52** — refreshed the post-persistence audit handoff. Its recorded baseline is historical now; this file supersedes it.

### Persistence / Quick Menu / OSC / keyboard/editor correctness

- **#23** — preserves unknown/future Quick Menu action IDs inertly and round-trippably.
- **#25** — recovers OSC profiles from damaged metadata while preserving valid siblings and avoiding stale duplicate resurrection.
- **#29** — hardens keyboard/profile persistence against wrong-typed and partially malformed state while preserving valid siblings and unknown future fields.
- **#30** — completed connected long-label expansion using pre-expansion graph topology.
- **#32** — rejects non-finite floating-control coordinates and recovers wrong-typed stored positions.
- **#33** — recovers malformed outside-stream orientation preference to Follow System.
- **#35** — recovers malformed floating-control reset-between-sessions state without discarding valid saved positions.
- **#37–#50** — hardens settings/profile/default/GL preference ownership, atomic profile storage, mutation-safe listeners, duplicate UUID handling, Gson boundary recovery, and global Settings reads.
- **#51** — rejects keyboard profile metadata entries that alias the same backing SharedPreferences storage.
- **#53** — allows modifier-only custom keys without requiring a non-modifier key.
- **#77** — resource-backs Artemis Action and Quick Menu catalog display metadata while preserving every stable persisted/runtime ID; an audit also promoted the new stable-ID/resource-resolution regression into the mandatory focused CI gate.

### Lifecycle / stream / controller ownership hardening

The lifecycle audit after #52 produced a long sequence of narrow fixes. Do not redo these from older audit queues:

- **#54** — owns smart reconnect across `Game` lifecycle.
- **#55** — hardens `NvConnection` start/stop ownership and native-bridge permit transfer.
- **#56** — stops commit-text work during stream teardown.
- **#57** — ignores late stream-surface callbacks after teardown.
- **#58** — binds delayed key releases to their owning connection.
- **#60** — releases digital keyboard input when controls detach.
- **#61** — releases non-digital keyboard input when controls detach.
- **#62** — hardens Wi-Fi monitor lifecycle ownership.
- **#63** — hardens native pointer-capture lifecycle ownership.
- **#64** — owns delayed `Game` callbacks across teardown.
- **#65** — prevents stale automatic input re-grab after lifecycle transitions.
- **#66** — owns `Game` connection callbacks across teardown/generation changes.
- **#67** — owns clipboard workers across `Game` lifecycle.
- **#68** — snapshots stop-worker transport state, suppresses stale UI completion, and preserves the explicit destroy-time Keep Alive cleanup path.
- **#70** — owns delayed USB-device callbacks across `UsbDriverService` lifecycle.
- **#71** — stops an already-running controller battery poll from re-posting itself after its `InputDeviceContext` is destroyed.
- **#72** — owns battery polling across Fast Resume / Keep Alive suspension, serializes final sends, drains old work before resume, and closes the suspension-boundary race.
- **#73** — owns Android controller sensor registration and final motion sends across reconnect suspension while preserving host-requested report rates for Keep Alive resume.
- **#74** — owns the recurring controller mouse-emulation loop across reconnect suspension, releases synthetic mouse buttons, neutralizes stale transient state, and resumes only active contexts.
- **#75** — owns transient controller rumble across reconnect suspension/final stop, cancelling Android, Shield/Sce, and USB haptics without replaying stale rumble on resume.

The controller delayed-work inventory is now closed for the demonstrated paths: stats-hold, battery polling, delayed sensor enablement, and mouse emulation are all either cancelled or lifecycle-owned.

### PiP / background product behavior already present

- **#41** guards Android 11 manual PiP entry while preserving the O–Q manual and Android 12+ auto-enter split.
- Fast Resume, Keep Connection Alive, foreground keep-alive service, headless decoder surface switching, timeout/wake-lock behavior, Keep Alive → Fast Resume fallback, and visible-surface restoration are implemented product systems, not missing features.
- Experimental Sideways CW/CCW streaming and outside-stream orientation policy are already present.

---

## 4. Major product systems already present

### Editor / keyboard / Artemis Actions

- UI Editor V4 gesture/persistence hardening.
- Named custom key/chord buttons, including modifier-only bindings.
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

The principal Artemis-owned persistence boundaries have been audited/hardened: settings profiles/default overlays, profile-editor serialization, Quick Menu, OSC metadata, keyboard profile/key/action metadata, GL preferences, floating controls, and outside-stream orientation.

Do not manufacture a broad persistence refactor from old notes. Re-open a persistence owner only when a concrete invariant/failure is demonstrated.

### 5.2 Deliberately unresolved keyboard recovery edge

If the entire keyboard-profile metadata blob is destroyed, inactive **geometry-only** dynamic backing stores can become unreachable. The active dynamic store is preserved, and key/action metadata can identify some inactive stores, but there is no deterministic ownership signal for geometry-only stores.

Do **not** add heuristic SharedPreferences scanning that may resurrect cleared, orphaned, partial-import, or unrelated stores. A recovery patch needs an authoritative ownership marker or migration scheme first.

### 5.3 Background/lifecycle ownership is much stronger than the old queue suggests

The former generic queue items around reconnect workers, clipboard workers, delayed callbacks, surface callbacks, stop workers, USB delayed callbacks, controller detach/polling, sensor enablement, mouse-emulation timers, and rumble have all received evidence-driven fixes and focused regressions.

A source-level re-check after #75 found no new reproducible ownership defect in the current Keep Alive → Fast Resume fallback/visible-surface restoration path. Continue to treat these as hardware-sensitive validation surfaces rather than rewriting them speculatively.

### 5.4 Controller LED behavior was reviewed and intentionally left stateful

Unlike transient rumble, host LED color is persistent state while Keep Alive retains the active connection generation. `LightsSession` ownership is carried across input-context migration and closed during context destruction. Android resolves light requests against the current device-light list, so an unplugged device does not justify adding speculative suspend/cache/replay machinery. Revisit only with a demonstrated device/OEM failure.

### 5.5 Repository/security hygiene sweep found no actionable leak

At the #75 baseline:

- build outputs/APKs/AABs/local SDK config/signing material are covered by `.gitignore`;
- no committed private-key marker or signing-password/keystore literal was found by repository code search;
- no tracked `.exe` or `.jks` artifact was found in the recursive tree review;
- no temporary controller staging patchers/workflows exist on `main`;
- disposable battery/sensor/mouse/rumble staging refs were repointed to the clean #75 `main` after use.

The tracked `.tflite` model is an intentional application asset, not generated build residue.

### 5.6 Action-catalog localization is merged

PR #77 moves Artemis Action and Quick Menu registry labels, categories, descriptions, picker text, accessibility labels, and related runtime messages from Java literals into Android string resources. Stable persisted/runtime IDs remain literal and unchanged. Temporary one-shot patchers/workflows from the staging branch were deliberately excluded.

This closes the catalog-specific resource-backing debt. It does not provide translations for every locale or claim that every Artemis Plus UI surface has completed a broader hard-coded-string/accessibility audit.

### 5.7 Hardware-sensitive behavior still needs physical validation

Robolectric/emulator/CI success does not prove OEM MediaCodec/TextureView/orientation/PiP/controller behavior. Real-device validation remains important for:

- MediaCodec output-surface switching;
- TextureView restoration timing;
- Keep Alive headless-output transitions and return/fallback;
- PiP enter/exit appearance;
- Sideways CW/CCW video and input transforms;
- IME/system-window behavior;
- controller disconnect/reconnect while backgrounded, including sensor/battery/rumble ownership;
- foldable cover/posture behavior.

The Diana audit did not find a complete reusable cover-screen controller/analog-trigger subsystem to port wholesale; future foldable work should be capability-gated.

### 5.8 New physical-device findings after #78

The user reported four concrete stream-editor defects and one requested key-button behavior after
testing the rolling debug build:

- a saved PC reachable through Tailscale can be shown as Offline;
- Quick Menu's touch-sensitivity action toggles the setting instead of opening the live editor;
- the keyboard-overlay editor gear and command strip can scale/reposition incorrectly after a
  display/orientation transition;
- an empty key row prevents an otherwise valid modifier-only custom key;
- the key picker can lose its results behind a docked/split IME;
- deposited key buttons need an optional, per-key, default-off long-press-to-toggle mode.

The Tailscale investigation found a concrete routing risk introduced when Keep Alive added
`CHANGE_NETWORK_STATE`: inherited STUN discovery could then bind the entire app process away from
the active VPN while host polls ran concurrently. PR #79 removes that process-wide reroute, keeps
host traffic on the VPN, and skips convenience WAN-address discovery while a VPN is active.

---

## 6. Next priorities

Keep unrelated fixes in separate coherent PRs.

1. **Reported device regressions** — finish and validate the Tailscale fix, then fix the concrete Quick Menu/editor/Add Keys/key-picker defects listed in §5.8.
2. **Optional per-key long-press toggle** — add the default-off setting without changing existing deposited-key press/release behavior.
3. **Lint cleanup** — triage the inherited 23-error/497-warning baseline in safe coherent batches; do not suppress findings merely to make the task green.
4. **Broader UI resource/accessibility follow-up** — audit remaining Artemis Plus UI strings and content descriptions. Actual locale translations are lower priority unless a contained translation batch is available.
5. **Test-suite cleanup** — make the inherited Robolectric/source-contract suite reliable across Linux and Windows, then strengthen the full-suite CI gate.
6. **Real-device lifecycle acceptance** — retain the broader Fast Resume, Keep Alive, controller, PiP, Sideways, and IME checklist below.
7. **Keyboard profile disaster recovery** — design an authoritative ownership marker/migration before attempting recovery of inactive geometry-only stores.

Persisted-state and repository-hygiene work should no longer be treated as generic open-ended priorities; revisit specific areas only when new evidence warrants it.

---

## 7. Real-device acceptance checklist

When a physical-device pass is available, prioritize:

- normal stream start/stop/reconnect;
- Fast Resume within/after timeout;
- Keep Alive supported, unsupported, service-start-failure, headless-switch-failure, and visible-surface-return-failure paths;
- background → foreground with controller connected/disconnected;
- battery polling stops in background and resumes once after foreground return;
- controller sensors do not re-register/send while suspended and restore current host report rates after return;
- mouse-emulation buttons/movement stop cleanly on background and resume without stale motion;
- rumble stops immediately on background and is not replayed after return;
- RGB LED state behaves sensibly through reconnect/device removal on target hardware;
- Android 11 manual PiP enter failure/success and Android 12+ auto-enter;
- PiP exit and overlay restoration;
- Sideways CW/CCW video + touch + custom controls;
- custom-key editor drag/resize/snap and connected long-label growth;
- profile export/import and profile corruption/recovery;
- Quick Menu nested pages/reset;
- OSC per-game mapping/stale repair/profile deletion;
- outside-stream orientation on the target OEM device.

Do not classify total input failure as a client regression until Apollo per-client input/launch permissions are checked.

---

## 8. Durable source-of-truth hierarchy

1. **Current source/tests + Git/Actions/Release history** — authoritative.
2. **`PROJECT_STATE.md`** — current roadmap/recovery map.
3. **`CODEX_HANDOFF.md`** — rolling latest coherent task/review packet.
4. **`AGENTS.md`** — stable operating/safety rules.
5. **`README.md` / `SIGNING.md`** — user-facing/release documentation.
6. **Old uploaded handoffs/chats** — historical intent only.

If these disagree, inspect current source/history and update stale documentation rather than coding from stale prose.
