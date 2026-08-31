# Artemis Plus — Codex Review Handoff

This is the **rolling review packet** for the latest coherent Codex/agent task.

`AGENTS.md` requires this file to be refreshed at the end of every completed task, committed on the task branch, pushed, and included in the pull request. The user can then ask ChatGPT or another reviewer to audit the exact branch/PR without relying on chat memory.

Git history preserves old versions of this file; keep the working copy focused on the latest task.

---

## Current handoff

## Task

Harden mixed-size keyboard editor snapping and connected-group geometry.

## User goal

Replace residual order-dependent snapping with deterministic best-candidate behavior, keep snapping separate from resize behavior, preserve groups while controls/labels change size, and publish the result independently from the Action-bundle audit.

## Repository state

- Base branch: `main`
- Base commit: `de32c77770346c7029dcde8011157b3ab302ed13`
- Task branch: `audit/deterministic-layout-snapping`
- Current product/code commit: `56bba14dcfef2f01e9313342e7ef73bb353b1b1d` (`Harden deterministic keyboard layout snapping`)
- Review-packet metadata is committed immediately after that product/code commit; the PR tip is authoritative.
- Pull request: [#13](https://github.com/juliekeygen-netizen/Artemis-plus/pull/13)
- PR state: open, unmerged; GitHub Actions status was not yet observed when this packet was written.
- Independent prerequisite context: Action-bundle audit [#12](https://github.com/juliekeygen-netizen/Artemis-plus/pull/12) is also unmerged, but this branch starts from clean `main` and does not depend on it.

## Scope completed

- Reworked `LayoutSnappingHelper` candidates into explicit axis/alignment objects. Horizontal and vertical candidates are selected independently by distance, alignment priority (gap/edge/center), then stable target-coordinate and alignment ties.
- Eliminated the remaining same-score dependency on `View` iteration order; a reordered set of equally eligible neighbors now produces the same snap coordinate.
- Kept the existing ~4 px spacing candidate priority and preserved position-only snapping: overlap does not change width or height, while group resize remains a separate path.
- Made the editor's lock-release hysteresis testable through the same helper used by the touch path.
- Fixed long-label expansion so an entire rightward connected branch, including stacked downstream members, moves with the newly widened button instead of losing its group connection.
- Centralized shared group-translation bounds in a pure helper used by `KeyBoardController`.
- Extended the permanent `LayoutSnappingHelperTest` selector already listed in Android CI; no CI configuration change was needed.

## Key implementation decisions

- Candidate scoring does not use child iteration position as a tie-break. Numeric target coordinates are stable across layout refresh/reordering, while explicit alignment priority maintains intentional gap-before-edge-before-center behavior.
- The helper remains geometry-only. It does not resize controls or infer a resize operation from overlap; `keyBoardVirtualControllerElement` retains resize/group-scale ownership.
- The new pure seams are called from the production move/label-expansion paths. This avoids an invalid Robolectric test that constructs `KeyBoardController`, a path that necessarily initializes native Moonlight code.
- No persisted format or grouping tolerance changed. Existing layouts, scaled group membership, and group-outline feedback continue to use their existing state ownership.

## Files changed

- `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/LayoutSnappingHelper.java`
- `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/keyBoardVirtualControllerElement.java`
- `app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java`
- `app/src/test/java/com/limelight/binding/input/virtual_controller/keyboard/LayoutSnappingHelperTest.java`
- `PROJECT_STATE.md`
- `CODEX_HANDOFF.md`

## Persistence / compatibility

- No preferences or serialized layout schema changed.
- Existing persisted geometry and scaled connected groups use the same `areGrouped()` rules as before.
- The behavioral change only chooses a stable position where pre-existing candidates tie and shifts previously stranded downstream connected controls during a text-width expansion.

## Lifecycle / race / safety review

- No `Game`, stream, Surface/TextureView, input suspension, background, PiP, or orientation code changed.
- Editor touch handling still owns sticky locks and group mode. The extracted hysteresis predicate is called from the same move path, preserving thresholds and teardown behavior.
- Group translation remains bounded to the parent frame before every member is updated, avoiding partial out-of-bounds movement.
- The group outline/view ordering logic remains unchanged and is still refreshed after group move/resize.

## Tests actually run

- `./gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "com.limelight.binding.input.virtual_controller.keyboard.LayoutSnappingHelperTest"` — PASS (20 tests).
- `./gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "com.limelight.binding.input.virtual_controller.keyboard.ArtemisActionButtonFactoryTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.KeyComboManagerTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.KeyboardProfilesManagerTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.LayoutSnappingHelperTest" --tests "com.limelight.binding.input.virtual_controller.keyboard.LongPressMoveGestureGuardTest"` — PASS.
- `./gradlew.bat :app:assembleNonRoot_gameDebug` — PASS.
- `git diff --check` — PASS.
- An initial transient `KeyBoardControllerTest` attempt — FAILED because constructing the controller invokes `PreferenceConfiguration.readPreferences()` and native `moonlight-core` loading under Robolectric. The invalid test was removed and replaced by runnable pure regression seams used directly in production; the failure is not retained or hidden.
- Full inherited Robolectric suite — NOT RUN; it includes documented baseline native/startup failures outside this focused patch.

## GitHub Actions / release

- PR: [#13](https://github.com/juliekeygen-netizen/Artemis-plus/pull/13)
- CI: pending/not yet observed at packet creation.
- Release/APK publication: not expected from this unmerged branch.
- Signing: unchanged; no signing material or build/release configuration changed.

## Known limitations / real-device validation

- Test the editor on a physical device with irregular mixed-size groups: drag around an equal-distance tie, deliberately detach/re-attach, move a group to each parent boundary, resize a scaled group, and edit a long custom-key label next to a stacked/rightward branch.
- Unit tests cannot validate finger feel, exact group-outline rendering, or all OEM coordinate behavior; no stream decoder behavior changed.

## Audit hotspots

- Check `Candidate.betterThan()` ordering against the intended UX for symmetric neighbors and gap-versus-edge/center ties.
- Check `KeyBoardController.ensureTextButtonWidth()` for any unusual group topology where a rightward member should not travel with the expanded branch.
- Check the touch path's sticky lock cleanup and group-mode exit behavior after a snap, cancel, configuration change, and profile switch.
- Verify legacy/scaled group tolerance has not unintentionally expanded; this patch intentionally leaves it unchanged.

## Deferred work

- Artemis Action profile-bundle audit remains on independent [#12](https://github.com/juliekeygen-netizen/Artemis-plus/pull/12), although its CI has passed.
- UI/localization polish, sideways-stream coverage, and foldable feasibility remain separate future phases.

## PROJECT_STATE update

`PROJECT_STATE.md` records the deterministic candidate, branch-preserving text expansion, and device-validation status for the snapping priority. It deliberately retains the unmerged Action-bundle investigation as Priority 1 because this independent branch started from clean `main`.

## Suggested reviewer action

Audit with special attention to symmetric-candidate tie behavior and real-device mixed-size editor interactions before merge.

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
