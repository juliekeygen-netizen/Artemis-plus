# Artemis Plus — Durable Project State and Roadmap

**Last refreshed:** 2026-08-31  
**Purpose:** current development handoff for Codex/ChatGPT and future contributors.  
**Update rule:** this file must describe the real current repository state, not an old wish list. Verify source/history before changing claims.

---

## 1. Project identity and direction

Artemis Plus is an Android game/desktop streaming client derived from the newer Marssvoodoo Artemis base. It keeps the newer Artemis/Moonlight reliability and streaming work while selectively porting or rethinking useful ideas from Diana OSC Suite and adding Artemis-specific UI/actions.

The project has grown into a larger quality-of-life/customization layer around:

- advanced on-screen controller (OSC) editing;
- custom keyboard keys/chords and local Artemis Action buttons;
- managed keyboard/control profiles;
- gamepad OSC profiles and per-game automatic selection;
- smart snapping/grouping/resizing and persistent editor geometry;
- a unified compact Artemis Plus editor/menu UI direction;
- Quick Menu customization;
- orientation/PiP/background-stream lifecycle improvements;
- experimental sideways/fake-portrait streaming;
- stable signed rolling debug APK builds.

General design principle: preserve the newer streaming base and extend it carefully. Do not replace working Artemis state ownership with parallel systems unless there is a demonstrated architectural reason.

---

## 2. Current durable baseline

At the time of this refresh, the latest verified `main` commit is:

`cc136900b15e00df3a62cf2f6d0d70d7c365d3bf`

Commit:

`Add automatic per-game OSC profile selection (#9)`

This baseline has post-merge Android CI and rolling debug-release verification from the previous work session.

Always check the actual remote/local `main` before starting a new task. This SHA is a recovery marker, not a forever-pinned base.

### Recent merged phase sequence

1. **PR #1 — First patch: UI Editor V4 hardening, outside-stream orientation, Settings search**
   - merged baseline commit: `3bf2660d0dafd8e3e92e1ab1d0fc31718fd497a9`
2. **PR #2 — Start gesture + PiP audit/fixes**
   - merged baseline commit: `c6611e51b9a724f0c91241051e53023d9161c367`
3. **PR #3 — Lifecycle hardening: overlays/settings/profile restoration**
   - merged baseline commit: `58b38a8fec47ce3c6bb3f6db27f11daeef759814`
4. **PR #4 — Fast Resume background streaming**
   - merged baseline commit: `61bc10e1babe98067f3996805985352a7ba61128`
5. **PR #5 — Remove conflicting `noHistory` Activity behavior**
   - merged baseline commit: `679932d49323be837074a07ec09382e3c2c65d54`
6. **PR #6 — Experimental Keep Connection Alive**
   - merged baseline commit: `a4aeb2b2792c5e0e0accf5e1a35b96456950e607`
7. **PR #7 — Customizable Quick Menu hierarchy/editor**
   - merged baseline commit: `a2c1c991bb573087ce6d398c7e1251da085a9996`
8. **PR #8 — Experimental sideways/fake-portrait stream orientation**
   - merged baseline commit: `6847e800264f47a8d7b2462876f07f4a4882a3c6`
9. **PR #9 — Automatic per-game OSC profile selection**
   - current main: `cc136900b15e00df3a62cf2f6d0d70d7c365d3bf`

Do not resurrect the older 2026-08-30 handoff's feature ordering blindly. Several items that were future plans there are now complete and merged.

---

## 3. Major completed systems

### 3.1 UI Editor V4 / floating-control hardening

The custom keyboard/action editor received a broad cleanup/hardening pass before the later feature phases.

Important durable behavior:

- editor/settings gear movement captures the original `ACTION_DOWN` coordinates before long-press arming, preventing the old jump when drag begins;
- movement is clamped to the logical editor area;
- a longer stationary hold can offer a reset-position action;
- delayed long-hold callbacks are explicitly cancelled during teardown/refresh;
- settings-button and native floating-control positions use normalized persistence rather than raw pixel-only restoration;
- portrait and landscape position slots are separate;
- reset-between-stream-sessions behavior was moved to real stream-session ownership instead of View attachment;
- floating Quick Menu and Zoom/Pan controls use persistent position behavior;
- custom keyboard/profile state restoration was hardened through configuration/lifecycle recreation.

Key files include:

- `KeyBoardController.java`
- `FloatingControlPositionStore.java`
- `PersistentPositionImageButton.java`
- `LongPressMoveGestureGuard.java` / tests

Do not reintroduce the old model where the long-press begins a drag without having captured the initial finger/view offset.

### 3.2 Shared Artemis Plus UI direction

`ArtemisEditorUi` is the shared UI helper/style direction for custom dialogs and editor/menu surfaces.

The preferred visual language is:

- compact dark surfaces;
- consistent title/text hierarchy;
- consistent row heights/padding;
- compact popups/context menus;
- selected/active state shown primarily through surface state rather than redundant labels;
- touch-friendly but not visually huge controls;
- popup placement that can flip above an anchor when space below is insufficient;
- avoidance of a patchwork of default Android AlertDialog/widget styling and custom styling.

Current code has partially/mostly migrated several surfaces, but final localization/menu polish is still a future cleanup area. When touching UI, extend the shared system rather than introducing another isolated style.

### 3.3 Custom keyboard keys/chords and Artemis Action buttons

The custom keyboard overlay supports named key/chord definitions and local Artemis actions.

Current local actions include, among others:

- Soft Keyboard
- Full Keyboard
- Rotate Screen
- Quick Menu
- Performance HUD
- Stats Overlay
- Floating Menu Button
- Touch Sensitivity
- Clipboard to PC
- Clipboard from PC
- Mouse Mode
- Toggle Zoom
- Gamepad Overlay
- Custom Buttons visibility

Action controls use Artemis-native-style icon buttons and deliberate direct-press behavior so sliding a held finger across neighboring controls cannot trigger dangerous local actions accidentally.

Key/chord runtime semantics are designed so modifiers stay down while ordered normal keys are pressed, then release in reverse order.

Keyboard profile management supports create/select/rename/duplicate/delete/reorder and legacy storage migration.

### 3.4 Smart snapping, grouping, resizing, persistence

The editor supports smart snapping, connected groups, group outlines, group move/resize flows, paired sizing, reset behaviors, and long-label horizontal expansion.

`LayoutSnappingHelper` has already been substantially improved and has regression tests. However, this remains a known user-visible weak area: mixed-size controls and best-candidate selection may still produce imperfect snapping.

Future work should not simply add more tolerance constants. The desired deterministic model is documented in the roadmap below.

### 3.5 Outside-stream orientation

A separate outside-stream orientation policy now exists and is intentionally distinct from the in-stream Rotate action.

Current Settings include behavior equivalent to:

- Follow system (default)
- Portrait

The policy is centralized through Artemis application/activity handling while excluding stream-specific Activities such as `Game`, shortcut trampoline paths, and external-display controller paths where appropriate.

Do not conflate outside-stream orientation with the older local Rotate action or the experimental sideways stream mode.

### 3.6 Settings search

Native Settings now has search support based on the actual runtime preference tree after device/API-specific preference removal.

Important behavior includes:

- title/summary/list-entry/category breadcrumb matching;
- result navigation back to the real preference;
- expansion of collapsed parent groups;
- adapter refresh/scroll/highlight behavior;
- Android-restorable Settings Fragment construction/lifecycle hardening.

Do not rebuild this feature from the older roadmap.

### 3.7 Bottom-edge Start gesture

The stream has a configurable bottom-edge Start gesture with modes:

- Native Windows touch / compatibility behavior
- Windows key
- Disabled

The gesture detector buffers bottom-edge touches while deciding whether the sequence is the gesture, replays ordinary input correctly, and avoids orphaned terminal events after recognition.

Permanent regression coverage exists.

### 3.8 PiP repair/hardening

PiP behavior was repaired to be transition-based rather than repeatedly resnapshotting overlay state.

The code now preserves/restores the real pre-PiP visibility of relevant overlays/controllers, uses explicit PiP callbacks, removes a conflicting fixed SurfaceHolder size behavior, constrains PiP ratios to Android-supported bounds, and uses real visible video bounds for transition hints with a fallback.

Sideways/fake-portrait mode intentionally does not pretend PiP is supported; that experimental path blocks/falls back where needed.

### 3.9 Fast Resume background streaming

Fast Resume is a safe retained-session mode that disconnects/stops the local Moonlight transport while allowing the hosted app/session to remain alive, then reconnects when returning within the configured timeout.

Important implementation decisions:

- normal app backgrounding can park the transport;
- controller/input is reversibly suspended rather than terminally destroyed;
- Android/USB/controller-touchpad input stays blocked while transport is intentionally down;
- controllers unplugged while parked are reconciled before restore;
- input only restores after a confirmed successful reconnect;
- Surface destruction ordering cannot accidentally trigger terminal teardown first;
- Wi-Fi performance locks are released while parked and reacquired for reconnect;
- explicit Disconnect/Quit, PiP, visible multi-window, configuration change, and external-display behavior stay separate.

### 3.10 Keep Connection Alive experimental mode

Keep Connection Alive extends background behavior by attempting to keep the active connection and MediaCodec stream running while the Activity is backgrounded.

Key architecture:

- API 23+ normal 2D path can switch decoder output from the Activity Surface to a continuously drained headless `ImageReader` Surface;
- foreground service is used while truly backgrounded;
- partial CPU wake lock is held while required;
- controller/input delivery remains suspended while headless;
- return switches decoder back to the visible Surface without reconnect when supported;
- timeout/manual-disconnect policies integrate with the existing background policy;
- async transport teardown has completion ownership so Surface/service/wake-lock lifetime cannot end before connection stop completes.

This remains experimental because vendor `MediaCodec.setOutputSurface()` support differs. Rejection or readiness races must fall back safely to Fast Resume rather than breaking the session.

### 3.11 `Game` Activity retention fix

`android:noHistory="true"` was removed from `Game` because Android's no-history contract directly conflicts with retained Fast Resume/Keep Alive behavior.

Normal disabled-background mode still explicitly performs its own teardown/finish behavior, so removal of `noHistory` should not be reversed casually.

Permanent manifest regression coverage exists.

### 3.12 Customizable Quick Menu

The in-stream Quick Menu is now a versioned persisted hierarchy rather than a fixed hard-coded two-page menu.

Current architecture includes:

- stable action IDs/registry;
- default tree preserving the prior root + Advanced conceptual layout;
- nested user-created subpages;
- runtime Back navigation;
- reorder/remove/rename/reset-defaults;
- Add Action and Add Subpage flows;
- searchable/category-filtered action picker;
- runtime-resolved dynamic Server Commands / Send Keys / device-specific actions rather than storing ephemeral host/controller entries;
- parser/editor nesting and global-node limits;
- malformed/unsupported config fallback;
- global Settings editor while avoiding misleading profile-scoped configuration.

Do not redesign the old hard-coded Quick/Advanced menus as if this phase never happened.

### 3.13 Experimental sideways / fake-portrait streaming

Phase 5 implemented the proof-of-concept requested to keep the Android Activity physically portrait while visually presenting a logical landscape stream rotated clockwise or counter-clockwise.

Current durable design:

- Off / Sideways CW / Sideways CCW modes;
- normal internal-display 2D path only; unsupported 3D/external-display cases fall back;
- sideways path uses `TextureView` so video can participate in the same transform as the logical stream UI;
- configured landscape decoder resolution remains logical landscape rather than being blindly inverted because the Android Activity is portrait;
- stream, overlays, virtual controllers, custom keyboard controls, full in-stream keyboard, and Quick Menu are transformed as a shared logical root;
- raw-coordinate editor/floating-control drags use inverse raw-to-logical mapping;
- CW and CCW floating-control persistence slots are separate from true portrait/landscape slots;
- Keep Alive can hand the TextureView decoder Surface to the existing headless Surface and restore it; vendor rejection still falls back safely;
- Surface attachment is identity-aware/idempotent to tolerate callback ordering;
- the Android IME action changes to the in-stream keyboard while sideways; separate-window key preview is suppressed because Android windows remain physically portrait;
- manual Rotate and PiP are blocked in sideways mode.

Known POC limitation: Android dialogs/windows/system UI that are separate from the rotated in-stream root may remain physically portrait. Final system-bar and OEM MediaCodec/TextureView behavior still needs real-device validation. The feature is experimental and off by default.

Permanent pure-policy/math regression coverage exists, but full lifecycle/visual integration coverage is lighter than ideal.

### 3.14 Automatic per-game gamepad OSC profile selection

PR #9 added automatic gamepad OSC profile mapping by streamed PC/app identity.

Key architecture:

- stable game key is host-scoped;
- PC UUID is preferred, host fallback exists;
- app UUID is preferred, app ID fallback exists;
- delimiter escaping prevents identity collisions;
- mapped profile is applied once per `VirtualController` instance before controls become visible;
- the existing OSC working-set and profile `switchProfile()` snapshot/restore path remain authoritative rather than creating a second layout state system;
- current game can assign/change/clear its automatic profile from the OSC Profiles menu;
- stale mappings self-repair if the mapped profile no longer exists;
- profile deletion removes its mappings.

The final PR head passed Android CI before merge, and post-merge Android CI + rolling debug APK publication were verified on `cc136900...`.

---

## 4. Build, CI, signing, and release invariants

### Signing

The project has a persistent signing identity so rolling APKs can update over the installed Artemis Plus build without data loss.

Previously verified certificate SHA-256:

`88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

Never regenerate/replace it casually and never commit/expose secrets.

### CI

GitHub Actions uses non-root debug compilation and mandatory Artemis Plus regression gates. Broader inherited Robolectric tests also run diagnostically.

The inherited baseline currently has five known Robolectric failures across legacy tests such as `LayoutInflationTest`, `SimpleStartupTest`, `StartupTest`, and `ProfilesNavigationTest`. They were reproduced from a pre-Artemis-Plus base and are documented in README. Do not hide them, but do not blame a new patch for them without evidence.

### Rolling debug APK

Successful `main` build publication maintains the `debug-latest` prerelease/tag and architecture-specific APKs plus checksum/signing metadata.

At the end of PR #9 work, `debug-latest` was verified to point to the same merged commit `cc136900...` and the stable signer remained intact.

---

## 5. Important diagnostic/history traps

### Apollo permissions can look like a client input regression

A newly re-paired/reinstalled Artemis client may stream successfully while Apollo has Mouse Input, Keyboard Input, Touch Input, Controller Input, or Launch Apps disabled for that paired client.

Symptoms previously included:

- video works;
- all client input appears dead;
- app launch can return HTTP 403 Permission denied.

Check Apollo permissions before rewriting Artemis input code.

### OEM orientation behavior is not generic Android behavior

Orientation requests have already behaved differently on real OxygenOS hardware than in emulator/standard expectations. Separate:

- outside-stream Activity orientation policy;
- in-stream local Rotate action;
- experimental physical-portrait/logical-landscape sideways mode.

Do not assume success in Robolectric/emulator proves OEM orientation UX.

### Old handoffs contain completed TODOs

The older detailed project handoff was useful for original requirements, but it predates PRs #1-#9. It described background streaming, PiP repair, outside-stream orientation, fake portrait, Start gesture, Settings search, and customizable Quick Menu as future work. Those are now implemented. Use the old handoff only as historical intent/edge-case context.

---

## 6. CURRENT ACTIVE INVESTIGATION — Artemis Actions in custom-key import/export

This is the immediate next work item, but **begin with an audit, not implementation**.

README still says support for putting Artemis Action entries directly into custom-key import/export JSON is planned. Current source indicates that statement may now be partially or fully stale.

### Verified current source behavior

`KeyboardProfilesManager` currently exports a versioned profile bundle with format:

`artemis-plus-keyboard-profiles`

Each exported profile contains:

- `name`
- `layout`
- `keys`
- `actions`

The `actions` field comes from:

`ArtemisActionButtonFactory.exportSelectionForLayout(...)`

Import of a modern bundle calls:

`ArtemisActionButtonFactory.importSelectionForLayout(...)`

for the profile's `actions` array.

Profile duplication also copies Artemis Action selections, and profile deletion clears them.

`ArtemisActionButtonFactory` uses stable action IDs and filters unknown action IDs during import rather than blindly restoring invalid entries.

Therefore the **modern keyboard-profile bundle already appears capable of round-tripping Artemis Action selections**.

### Existing test gap discovered

`KeyboardProfilesManagerTest` verifies:

- legacy layout migration;
- create/rename/duplicate/reorder/delete behavior;
- final-profile deletion protection;
- legacy import appends without replacing active profile;
- bundle export includes all profiles.

It does **not** currently appear to directly prove that an exported bundle containing Artemis Action selections imports those selections correctly into the newly created profile. This is a valuable regression gap even if no product code change is needed.

### What remains unknown and must be traced

`KeyComboManager` still has legacy key-definition serialization/parsing behavior that is key-only. Before changing it, determine whether that legacy format is still exposed as a distinct user-facing import/export path, or whether the modern profile-bundle UI is the real/current import/export surface.

Inspect at minimum:

- `KeyboardProfilesManager.java`
- `KeyboardProfilesManagerTest.java`
- `KeyboardProfilesDialog.java`
- `ArtemisActionButtonFactory.java`
- `ArtemisActionButtonFactoryTest.java`
- `KeyComboManager.java`
- `KeyComboManagerTest.java`
- `KeyBoardController.java`
- Settings/profile UI import/export callers
- any file picker/share/export flows touching these APIs

### Decision rule

#### Case A — modern user-facing import/export already includes actions

Do **not** build another serialization system.

Preferred task:

1. add direct bundle round-trip regression coverage for keys/actions/layout as appropriate;
2. verify malformed/unknown action behavior;
3. correct README wording so it no longer claims the implemented behavior is planned;
4. clarify that any legacy key-only JSON remains legacy/keys-only if that is intentional;
5. keep the patch small and architecture-preserving.

#### Case B — a separate exposed legacy custom-key JSON flow genuinely needs actions

Preserve backward compatibility.

Preferred architecture:

- old plain key-only JSON must remain importable;
- add a versioned object/envelope rather than pretending Artemis-local actions are keyboard keys;
- keep local actions represented by stable action IDs;
- keep profile/layout scope explicit;
- ignore unknown action IDs safely;
- do not break existing Artemis/Diana layout imports;
- add migration/round-trip/unknown-ID regressions.

Do not implement Case B until callers prove it is necessary.

---

## 7. Prioritized roadmap after the current investigation

Priorities may change based on new user feedback or audit findings. The current recommended order is:

### Priority 1 — Resolve Artemis Action import/export truth + regression coverage

See section 6. This should likely be a relatively contained audit/fix/docs phase.

### Priority 2 — Mixed-size snapping / deterministic best-candidate behavior

This is the strongest known editor-quality weak spot after current feature phases.

Desired behavior:

- standard ~4 px inter-control group gap;
- reliable left/right/top/bottom edge alignment;
- center alignment where useful;
- mixed-size control support;
- nearest/best candidate wins instead of whichever neighbor is processed last;
- deterministic candidate scoring/tie-breaking;
- hysteresis so tiny finger movement does not immediately tear a control from a group;
- no surprise resizing merely because controls overlap;
- group feedback outline while snapping/transforming;
- preserve connected-group movement semantics;
- avoid making long-label auto-expansion collide with its neighbors.

Recommended implementation approach:

1. audit `LayoutSnappingHelper` and `LayoutSnappingHelperTest` as they exist now;
2. represent snap candidates explicitly with axis, target coordinate, distance/error, alignment type, and source/target control context;
3. score/select the best candidate per axis deterministically;
4. separate snapping from resizing/overlap correction;
5. introduce hysteresis/attachment tolerance consciously rather than through accidental broad tolerance;
6. add mixed-size, competing-neighbor, tie, scaled-group, and detach/re-attach regressions;
7. verify group outline/UX on real layouts.

### Priority 3 — Final localization/menu/UI polish

The shared UI direction exists, but some surfaces still contain hard-coded English strings or isolated raw `AlertDialog` styling.

Audit for:

- hard-coded strings in Artemis Plus additions;
- profile delete/rename dialogs and related menu consistency;
- Quick Menu/editor action labels;
- Settings strings/summaries;
- consistent typography, row heights, widths, spacing;
- popup behavior on small/portrait layouts;
- action/key editor text hierarchy;
- accessibility/content descriptions where custom controls were introduced.

Avoid mixing this with unrelated streaming architecture changes unless a UI fix genuinely requires them.

### Priority 4 — Strengthen sideways-stream integration coverage

Pure mapping/policy coverage exists, but the experimental feature still depends on real Activity/View/Surface integration.

Potential test targets where practical:

- logical root transform installation/restoration;
- raw-to-logical editor drag mapping across CW/CCW;
- overlay visibility/state when toggling modes;
- TextureView Surface replacement idempotence;
- Keep Alive headless Surface handoff/return policy seams;
- orientation/configuration recreation behavior;
- unsupported-mode fallback.

Do not attempt to fake native MediaCodec behavior in Robolectric if the test would be meaningless. Preserve explicit real-device test requirements.

### Priority 5 — Diana foldable features

Still not ported:

- cover-screen trigger controller;
- analog trigger emulation;
- fuller profile-overlay/cover-screen UX.

Before implementation:

- audit the exact Diana code/dependencies against the newer Artemis base;
- separate generally useful controller behavior from device/foldable-only hooks;
- avoid pulling broad old-branch dependencies merely to get one feature;
- determine sensor/display/fold-state APIs and device support;
- design fallbacks so non-foldable devices are unaffected;
- create a POC branch first for hardware-specific behavior.

This should not be prioritized ahead of known user-visible editor issues unless the user specifically asks for it.

---

## 8. Known limitations / real-device validation areas

### Keep Connection Alive

- vendor `MediaCodec.setOutputSurface()` support is device/codec dependent;
- foreground-service and battery behavior should be verified on real devices;
- fallback to Fast Resume must remain reliable.

### Sideways/fake-portrait mode

- system bars and separate Android windows/dialogs remain physical-portrait concerns;
- TextureView/MediaCodec behavior is vendor dependent;
- IME and pointer behavior should be validated physically;
- real device testing is still authoritative for final UX.

### In-stream Rotate action

Historically unreliable on real OxygenOS even after compatibility/retry work. It is not the same feature as outside-stream orientation or sideways mode. Do not sink unrelated roadmap time into it unless the user asks or a new regression is found.

### Snapping

Mixed-size layouts remain the most credible known editor UX weak spot despite existing tests and helper improvements.

---

## 9. High-value code map

### Streaming/lifecycle/orientation

- `app/src/main/java/com/limelight/Game.java`
- `app/src/main/AndroidManifest.xml`
- `SidewaysStreamMode.java`
- background streaming policy/helper classes
- decoder/render Surface/TextureView integration
- PiP helper/state classes

### Quick Menu

- `GameMenu.java`
- Quick Menu config/tree/action registry/editor classes
- Settings entry/config persistence

### Custom keyboard/action editor

- `KeyBoardController.java`
- `KeyBoardControllerConfigurationLoader.java`
- `KeyBoardDigitalButton.java`
- `keyBoardVirtualControllerElement.java`
- `KeyComboManager.java`
- `KeyboardProfilesManager.java`
- `KeyboardProfilesDialog.java`
- `ArtemisActionButton.java`
- `ArtemisActionButtonFactory.java`
- `LayoutSnappingHelper.java`

### Gamepad OSC profiles

- `OscProfilesManager.java`
- `OscProfileDialog.java`
- `OscGameProfileKey.java`
- `VirtualController.java`

### Shared UI / floating controls

- `ArtemisEditorUi.java`
- `FloatingControlPositionStore.java`
- `PersistentPositionImageButton.java`

### Tests/CI

- `app/src/test/...` Artemis Plus regression classes
- `.github/workflows/android-ci.yml`
- `.github/workflows/build-debug-apk.yml`
- `build-apk.ps1`
- `SIGNING.md`

---

## 10. Definition of a good next Codex task

A high-quality autonomous task should:

1. sync/inspect `main` and read `AGENTS.md` + this file;
2. select the requested or highest-priority coherent item;
3. investigate actual callers/state ownership before editing;
4. create a feature/fix/audit branch;
5. implement a complete coherent patch, not scattered unrelated changes;
6. add/strengthen regression coverage;
7. run focused tests during iteration and broader validation before handoff;
8. inspect the final diff for accidental churn/temp files;
9. update this file if the durable state/roadmap changed;
10. refresh `CODEX_HANDOFF.md` with the exact audit packet;
11. commit and push;
12. open/update a PR targeting `main`;
13. leave it unmerged by default for external audit unless the task explicitly says to merge.

That branch/PR + `CODEX_HANDOFF.md` is the artifact the user can hand to ChatGPT for a second-pass review.
