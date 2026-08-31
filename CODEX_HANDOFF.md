# Artemis Plus — Codex Review Handoff

This is the **rolling review packet** for the latest coherent Codex/agent task.

`AGENTS.md` requires this file to be refreshed at the end of every completed task, committed on the task branch, pushed, and included in the pull request. The user can then ask ChatGPT or another reviewer to audit the exact branch/PR without relying on chat memory.

Git history preserves old versions of this file; keep the working copy focused on the latest task.

---

## Current handoff

### Task

Compact editor UI/localization audit.

### User goal

Make Artemis Plus editor and menu surfaces more coherent without changing streaming, controller,
profile, or persisted-layout behavior. Focus on profiles/context menus, Add/Edit Key, Add Artemis
Actions, Quick Menu, Touch Sensitivity, OSC profiles/per-game selection, and the related Settings
entries. Prefer `ArtemisEditorUi`, move appropriate hard-coded English to resources, and keep
short/portrait layouts usable.

### Repository state

- Base branch: `main`
- Base commit: `de32c77770346c7029dcde8011157b3ab302ed13`
- Task branch: `audit/editor-ui-localization`
- Product commit: `f80c4a68e4993bf3487942df6f3a0b1309d95aec`
- Current handoff commit: this commit (the PR tip includes this packet)
- Pull request: pending publication
- PR state: local validation complete; ready to push and open for review

### Scope completed

- Converted profile deletion, custom-key deletion, Quick Menu reset, and every OSC profile
  selector/create/rename/delete dialog to the shared compact dark `ArtemisEditorUi` surface.
- Moved the user-facing text used by custom keyboard profiles, custom keys, Artemis action picker,
  Quick Menu, touch sensitivity, OSC profiles, and keyboard-profile import feedback into default
  string resources. Dynamic Quick Menu subpage and imported-profile counts use Android plurals.
- Converted the Artemis Settings Quick Menu and keyboard-profile import/export titles/summaries to
  resource references.
- Wrapped the Quick Menu editor and Touch Sensitivity content in bounded scroll containers with
  compact height caps, preserving access to action/footer controls on short displays.
- Audited the Send Keys and Server Commands menus: they already use resource-backed labels and the
  shared compact menu/dialog path, so no redundant implementation was added.

### Key implementation decisions

- Reused `ArtemisEditorUi.builder()` and `styleDialog()`; no parallel dialog styling system or
  profile state was introduced.
- OSC profile dialogs retain `OscProfilesManager` as the sole profile/mapping owner. The change is
  display/resource plumbing only; switch, save, set/clear per-game mapping, and deletion ordering
  are unchanged.
- Quick Menu retains its versioned `QuickMenuConfig` tree and existing node/depth limits. The
  editor is scrollable only at the UI container level; action registration, persistence, and
  runtime navigation are unchanged.
- New keys are default-resource fallbacks. Existing locale-specific files remain valid and fall
  back until translators provide localized values.

### Files changed

- Shared/editor behavior: `Game.java`, `QuickMenuEditorDialog.java`, `OscProfileDialog.java`
- Keyboard/action UI: `ArtemisActionButtonFactory.java`, `KeyComboManager.java`,
  `KeyboardProfilesDialog.java`
- Settings/resources: `StreamSettings.java`, `res/values/strings.xml`, `res/xml/preferences.xml`
- Durable roadmap: `PROJECT_STATE.md`

### Persistence / compatibility

- No preference keys, keyboard-profile JSON/bundle schema, custom-key definition format, Quick
  Menu format, OSC profile format, or per-game mapping behavior changed.
- Keyboard-profile import still appends as before; only its empty/success messages are now
  resource/plural-backed.
- Unknown/corrupt profile/action/key handling is untouched.

### Lifecycle / race / safety review

- No changes to stream lifecycle, PiP, background streaming, Surface/TextureView ownership,
  input suspension, or controller state.
- Touch sensitivity keeps the exact existing preference writes and reads; only its dialog is
  resource-backed and bounded-scroll.
- Short-layout safety was improved by making the Quick Menu and sensitivity content scrollable
  inside capped dialogs. Nested Quick Menu list behavior still needs device validation.
- Popup anchoring for keyboard-profile context actions remains the existing above/below placement
  calculation; only the destructive confirmation received the shared dialog treatment.

### Tests actually run

- `./gradlew.bat :app:compileNonRoot_gameDebugJavaWithJavac` — PASS
- `./gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "com.limelight.quickmenu.QuickMenuConfigTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.ArtemisActionButtonFactoryTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.KeyComboManagerTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.KeyboardProfilesManagerTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.LayoutSnappingHelperTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.LongPressMoveGestureGuardTest"` — PASS (28 tests)
- `./gradlew.bat :app:assembleNonRoot_gameDebug` — PASS
- `git diff --check` — PASS
- Full inherited unit suite — NOT RUN; it has documented legacy/native Robolectric baseline
  failures and this UI-only change was covered by the relevant selector suite plus compilation.

### GitHub Actions / release

- CI result: not yet run; branch has not been pushed at handoff creation.
- APK/release publication: not requested and not performed.
- Signing material/configuration: untouched.

### Known limitations / real-device validation

- Validate profile/key/Quick Menu/OSC dialogs on a short portrait device and with a long translated
  locale, including footer reachability, popup width, Quick Menu nested scrolling, and IME focus.
- This patch does not translate every locale file; new values fall back to English until
  translations are supplied.
- No visual emulator/device capture was available in this task.

### Audit hotspots

- `QuickMenuEditorDialog.show()`: verify the outer scroll container cooperates naturally with the
  fixed-height nested `RecyclerView` on touch devices.
- `OscProfileDialog`: verify all selection and confirmation dialogs have the intended dark surface
  when opened over a stream and from Settings.
- `KeyComboManager` and `KeyboardProfilesDialog`: verify confirmation callbacks still mutate only
  the selected active profile and the popup continues to flip above its anchor when required.
- `strings.xml`: verify resource formatting/plurals in downstream localization tooling.

### Deferred work

- Broader legacy Settings/raw-dialog cleanup is intentionally deferred: it should be a separate
  audit, not mixed with current profile/Quick Menu behavior.
- Locale-specific human translations and real-device visual QA remain future work.
- Phase 1 action-profile import/export and Phase 2 snapping fixes are separate unmerged branches;
  this branch does not assume or duplicate them.

### PROJECT_STATE update

Updated the durable main recovery marker to the actual current `main` commit and recorded the
editor UI/localization design, compatibility guarantees, and remaining device/localization work.

### Suggested reviewer action

Audit with special attention to compact dialog scrolling and profile/OSC confirmation behavior;
real-device UI validation before merge is recommended.

---

# Required template for future tasks

Replace the **Current handoff** section above with the following information for each coherent task. Do not leave placeholders in a completed handoff.

## Task

Short feature/fix/audit name.

## User goal

Describe the requested user-visible behavior and any important constraints.

## Repository state

- Base branch:
- Base commit:
- Task branch:
- Current head commit:
- Pull request number/URL:
- PR state: draft / ready / CI running / review requested / etc.

## Scope completed

Explain exactly what the task implemented or fixed. Group by subsystem when useful.

## Key implementation decisions

Document the decisions a reviewer needs to understand, especially:

- state ownership;
- why a particular lifecycle hook/timing point was chosen;
- compatibility/migration strategy;
- fallbacks;
- malformed/stale-data behavior;
- why an existing architecture was reused instead of replaced;
- any intentional behavior differences from the original request.

## Files changed

List materially changed paths, grouped if there are many. Do not hide temporary/generated/unrelated files; ideally remove them before handoff.

## Persistence / compatibility

State what existing preferences/layouts/profiles/import formats continue to work and how migrations or repairs behave.

## Lifecycle / race / safety review

Record the relevant checks performed, for example:

- Activity/Fragment recreation;
- configuration/orientation changes;
- PiP/multi-window;
- background/foreground streaming;
- Surface/TextureView/decoder transitions;
- controller/input suspend/resume;
- delayed callbacks/teardown;
- duplicate callbacks/idempotence;
- corrupt/stale SharedPreferences;
- performance/GC/UI-thread concerns.

Do not include irrelevant headings just to fill space; include the ones that matter for the task.

## Tests actually run

For every command/suite, state its real outcome.

Example:

- `./gradlew ... --tests ...` — PASS
- non-root debug Java compile — PASS
- full inherited suite — completed with the documented baseline failures only

If something was not run, say `NOT RUN` and why.

## GitHub Actions / release

- Workflow/run IDs or links if available:
- CI result:
- APK build result when relevant:
- rolling `debug-latest` state when relevant:
- signing verification when relevant:

Never imply CI/release success before it is actually observed.

## Known limitations / real-device validation

List anything CI cannot prove, especially OEM orientation, MediaCodec Surface switching, PiP appearance, touch transforms, battery/foreground-service behavior, and hardware-specific features.

## Audit hotspots

Tell the reviewer where failure is most plausible. Name files/functions/state transitions and what invariants should be checked.

## Deferred work

List intentionally deferred items and why they were not mixed into the current patch.

## PROJECT_STATE update

Explain what was changed in `PROJECT_STATE.md`, or explicitly state that no durable roadmap/state change was required.

## Suggested reviewer action

One of:

- audit and, if clean, merge;
- audit with special attention to named risks;
- real-device test before merge;
- further Codex implementation required before review.
