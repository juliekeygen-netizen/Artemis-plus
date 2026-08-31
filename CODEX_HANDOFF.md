# Artemis Plus — Codex Review Handoff

This is the **rolling review packet** for the latest coherent Codex/agent task.

`AGENTS.md` requires this file to be refreshed at the end of every completed task, committed on the task branch, pushed, and included in the pull request. The user can then ask ChatGPT or another reviewer to audit the exact branch/PR without relying on chat memory.

Git history preserves old versions of this file; keep the working copy focused on the latest task.

---

## Current handoff

## Task

Audit and harden Artemis Action selections in keyboard-profile import/export.

## User goal

Establish whether the exposed custom-key/profile transfer flow already supports Artemis-local Actions, avoid a redundant format if it does, and provide durable regression coverage and accurate documentation.

## Repository state

- Base branch: `main`
- Base commit: `de32c77770346c7029dcde8011157b3ab302ed13`
- Task branch: `audit/action-profile-bundle-regressions`
- Current product/code commit: `d59fa43f3c6b73303a03af9bf9e72b64866ee451` (`Audit keyboard profile action bundles`)
- Review-packet metadata is committed immediately after that product/code commit; the PR tip is authoritative.
- Pull request: [#12](https://github.com/juliekeygen-netizen/Artemis-plus/pull/12)
- PR state: open, unmerged; GitHub Actions status was not yet observed when this packet was written.

## Scope completed

- Traced all direct keyboard profile transfer callers. Settings' JSON file picker and sharing path both use `KeyboardProfilesManager`; it exports/imports the versioned `artemis-plus-keyboard-profiles` bundle with per-profile `layout`, `keys`, and `actions` fields.
- Confirmed that `KeyboardProfilesDialog` operates on the same profile metadata and that controller refresh/switch uses the active profile storage selected by `KeyboardProfilesManager`.
- Confirmed that `KeyComboManager` remains intentionally key/chord-only within the bundle's `keys` field. The older `import_special_button_file` path is independent, import-only legacy data held in `GameMenu.KEY_NAME`, not a competing profile export format.
- Added direct modern-bundle round-trip coverage for profile-scoped layout geometry, custom key definitions, and Action IDs; it also proves import appends profiles and preserves the pre-existing active profile.
- Hardened Action selection import to discard unknown/stale Action IDs, and export to emit known IDs in stable enum order rather than preserving stale metadata or depending on `HashSet` iteration.
- Corrected README wording and advanced the durable roadmap to the snapping work.

## Key implementation decisions

- Existing `KeyboardProfilesManager` ownership is retained. No second serialization system or fake PC-key representation for local Actions was introduced.
- Action selections remain a per-layout `SharedPreferences` string set, represented in bundles by stable `ArtemisAction` IDs only.
- Unknown Action IDs are intentionally dropped when importing into this client. Valid IDs remain forward-safe and stale IDs from older metadata are not propagated through an export.
- Legacy plain-layout JSON remains compatible as an additional geometry-only profile with empty custom-key and Action selections; import never replaces the active profile or current profile list.

## Files changed

- `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/ArtemisActionButtonFactory.java`
- `app/src/test/java/com/limelight/binding/input/virtual_controller/keyboard/KeyboardProfilesManagerTest.java`
- `README.md`
- `PROJECT_STATE.md`
- `CODEX_HANDOFF.md`

## Persistence / compatibility

- Existing profile storage, legacy layout migration, plain Artemis/Diana layout imports, custom key definitions, and selected Action IDs remain compatible.
- Imported modern bundles add new profiles with fresh storage names; their geometry, keys, and Actions do not overwrite the existing active profile.
- Missing `actions` continues to import as no Action selections. Unknown Action IDs are safely ignored; duplicate known IDs naturally collapse in the set.

## Lifecycle / race / safety review

- This patch does not touch `Game`, stream lifecycle, decoder/Surface handling, controller suspension, PiP, orientation, delayed callbacks, or UI view ownership.
- The only runtime persistence change is bounded to the profile Action-selection set. A small fixed enum iteration replaces non-deterministic set iteration during export, with no meaningful performance/GC impact.
- Existing Settings error handling remains responsible for malformed root JSON; the added regression specifically covers stale individual Action IDs without corrupting profile selection metadata.

## Tests actually run

- `./gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "com.limelight.binding.input.virtual_controller.keyboard.ArtemisActionButtonFactoryTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.KeyComboManagerTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.KeyboardProfilesManagerTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.LayoutSnappingHelperTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.LongPressMoveGestureGuardTest"` — PASS.
- `./gradlew.bat :app:assembleNonRoot_gameDebug` — PASS.
- `git diff --check` — PASS.
- Full inherited Robolectric suite — NOT RUN; this focused patch exercised the permanent keyboard regression selectors, while the documented baseline failures remain outside scope.

## GitHub Actions / release

- PR: [#12](https://github.com/juliekeygen-netizen/Artemis-plus/pull/12)
- CI: pending/not yet observed at packet creation.
- Release/APK publication: not expected from this unmerged branch.
- Signing: unchanged; no signing material or build/release configuration changed.

## Known limitations / real-device validation

- No physical-device validation is needed for the pure JSON/profile persistence change before code review.
- Standard device validation after merge can confirm Settings import/share UX with a bundle containing actions, custom keys, and a legacy layout file; no stream/MediaCodec behavior changed.

## Audit hotspots

- Review `KeyboardProfilesManager.importProfiles()` alongside `ArtemisActionButtonFactory.importSelectionForLayout()` to confirm that actions are assigned only to the fresh imported storage name and that unknown ID dropping matches the compatibility policy.
- Review the new test's state reset to ensure it models an existing active profile without relying on internal profile IDs.
- Verify README’s distinction between the profile bundle and legacy geometry-only payload against any future Settings transfer UI changes.

## Deferred work

- Deterministic mixed-size snapping, group hysteresis, and long-label neighbor handling are intentionally deferred to the next independent phase.
- Broader UI/localization polish, sideways-mode coverage, and foldable feasibility are not mixed into this serialization audit.

## PROJECT_STATE update

`PROJECT_STATE.md` now records the completed audit, the actual profile-bundle and legacy-path ownership, the new stale-ID behavior, and moves deterministic snapping to the first recommended remaining priority. The durable `main` baseline SHA was intentionally not changed because this PR is unmerged.

## Suggested reviewer action

Audit and, if clean, merge.

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
