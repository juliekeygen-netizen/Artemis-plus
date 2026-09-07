# Artemis Plus — Codex Review Handoff

This is the rolling review packet for the latest coherent task. Inspect live GitHub state before
relying on recorded SHAs.

---

## Task

Fix the reported in-stream editor regressions and add optional per-key hold-to-toggle behavior.

## Repository state

- Branch: `fix/stream-editor-device-regressions`
- Base: `main` at `0f04fe66c33f3828d68d9c363f045105b75472ce`
- Product implementation commit: `a3516d96`
- Pull request: [#80](https://github.com/juliekeygen-netizen/Artemis-plus/pull/80)
- PR state at packet creation: open; exact-head CI pending

## Intent and user-visible behavior

- Quick Menu's touch-sensitivity entry opens the live adjustment dialog instead of silently
  cycling sensitivity.
- The keyboard-overlay editor gear remains compact after orientation/display changes, and the
  command strip remains centered without overlapping it.
- Modifier-only key buttons save even when the convenience key row is still present and empty.
- Key selection uses an embedded, searchable modal list that resizes with docked/split IMEs;
  keyboard Done selects the best result even when the IME consumes nearly all landscape height.
- Each deposited key can independently enable a default-off “Hold to toggle this key” option.
  Long-pressing latches the chord; pressing/holding it again releases it without relatching.

## Implementation summary

- `GameMenu` dispatches `SWITCH_TOUCH_SENSITIVITY` to the existing live editor dialog.
- `KeyBoardController` now treats the laid-out overlay container as the editor-chrome coordinate
  owner. A layout listener recalculates compact short-edge sizing and centered placement after
  rotation, and is removed during teardown.
- `KeyComboManager` ignores empty key rows, auto-names valid modifier-only buttons, and replaces
  `AutoCompleteTextView`'s windowed dropdown with a resize-aware search dialog and embedded list.
- `KeyComboManager.Definition` persists `longPressToggle`; missing legacy JSON defaults to false.
- `KeyComboButton` owns the latch/unlatch state and guarantees reverse-order releases on the
  unlock gesture, definition replacement, and view detachment.
- Touched sorting paths use Android-5-compatible `Collections.sort`, removing eight inherited
  API-24 lint errors.
- Focused editor geometry, key persistence, and input-event tests are in the mandatory CI gate.

## Materially changed files

- `app/src/main/java/com/limelight/GameMenu.java`
- `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java`
- `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyComboButton.java`
- `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyComboManager.java`
- `app/src/main/res/values/artemis_action_catalog.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardEditorChromeLayoutTest.java`
- `app/src/test/java/com/limelight/binding/input/virtual_controller/keyboard/KeyComboButtonToggleTest.java`
- `app/src/test/java/com/limelight/binding/input/virtual_controller/keyboard/KeyComboManagerTest.java`
- `.github/workflows/android-ci.yml`
- `PROJECT_STATE.md`
- `CODEX_HANDOFF.md`

## Architecture, compatibility, lifecycle, and performance

- No parallel overlay state system was added. Existing editor Views, profile persistence, and
  host key-event delivery remain the owners.
- Existing key definitions remain compatible because the new JSON property is optional and
  defaults off. No eager migration or format-version bump is required.
- Quick Menu stable action IDs and saved menu layouts are unchanged.
- The layout listener is scoped to the controller lifetime; delayed relayout uses the actual
  current `FrameLayout` bounds and ignores detached editor controls.
- Filtering remains off the UI thread through Android's `Filter`; the embedded list avoids a
  separate popup window fighting IME insets.
- A latched chord is explicitly released if its definition changes or its View detaches, avoiding
  stuck host keys during profile/layout refreshes.

## Validation actually run

- `KeyBoardEditorChromeLayoutTest` — PASS (2 tests).
- `KeyComboButtonToggleTest` — PASS (2 tests).
- `KeyComboManagerTest` — PASS (10 tests, including legacy/default-off persistence).
- `QuickMenuConfigTest` — PASS (12 tests).
- `:app:compileNonRoot_gameDebugJavaWithJavac` — PASS.
- `:app:assembleNonRoot_gameDebug` — PASS, including all four configured ABIs.
- `:app:lintNonRoot_gameDebug` — expected inherited failure, improved from 23 errors/497 warnings
  to 15 errors/497 warnings; no new warning was added. The remaining baseline is the next Q4 task.
- `git diff --check` — PASS before documentation update.

## CI and release state

- PR #80 exact-head push and pull-request CI: pending at packet creation.
- No signed rolling APK is published from feature branches. A successful merge to `main` should
  trigger the established signed four-ABI `debug-latest` workflow.

## Required device follow-up

- Rotate while the keyboard-overlay editor is open and verify the gear stays compact and the four
  command buttons remain centered without overlap.
- Exercise key search with floating, docked, and split OEM keyboard modes; verify list taps and
  keyboard Done both select the intended best match.
- Verify modifier-only creation with the empty row untouched.
- Verify ordinary deposited keys remain momentary by default, while enabled keys latch on long
  press and release on the next press/hold.
- Verify the Quick Menu entry opens the live sensitivity editor during a stream.

## Audit hotspots and deferred work

- Review chord release ordering and teardown carefully; a regression can leave a host key pressed.
- The remaining lint baseline, broader string/resource audit, and accessibility audit stay in
  separate coherent phases. Actual translations remain lower priority.
- Broader lifecycle/controller/PiP/Sideways acceptance still requires physical hardware.
