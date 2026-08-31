# Artemis Plus — Codex Review Handoff

This is the **rolling review packet** for the latest coherent Codex/agent task.

`AGENTS.md` requires this file to be refreshed at the end of every completed task, committed on the task branch, pushed, and included in the pull request. The user can then ask ChatGPT or another reviewer to audit the exact branch/PR without relying on chat memory.

Git history preserves old versions of this file; keep the working copy focused on the latest task.

---

## Current handoff

### Task

Sideways/fake-portrait stream regression coverage audit.

### User goal

Strengthen meaningful coverage for the experimental sideways stream implementation without
pretending Robolectric can validate vendor TextureView/MediaCodec behavior or rewriting the
feature merely to increase test count.

### Repository state

- Base branch: `main`
- Base commit: `de32c77770346c7029dcde8011157b3ab302ed13`
- Task branch: `audit/sideways-stream-regressions`
- Product commit: `0e1018583f84e06205746ce5575a8dac5102c420`
- Current handoff commit: this commit (the PR tip includes this packet)
- Pull request: pending publication
- PR state: local validation complete; ready to push and open for review

### Scope completed

- Added `SidewaysStreamLayoutTest`, a Robolectric layout-level regression suite for the physical
  portrait root. It verifies the logical canvas dimensions, centering, pivot, and CW/CCW rotation,
  plus restoration to an ordinary physical canvas when an unsupported mode normalizes to off.
- Extended `FloatingControlPositionStoreTest` so reset clears portrait, landscape, sideways-CW,
  and sideways-CCW normalized position slots.
- Updated project state with the transform/persistence coverage boundary and the remaining
  device-only TextureView/MediaCodec limitation.

### Key implementation decisions

- Tests use the real `SidewaysStreamLayout` ViewGroup measurement/layout code rather than only
  reasserting `SidewaysStreamMode` math helpers.
- No surface lifecycle helper was fabricated merely for unit testing. `StreamContainer` ownership
  of a real `SurfaceTexture`/decoder transition remains device/vendor dependent and is documented
  for physical validation.
- No production code, stream lifecycle, logical coordinate mapping, position persistence format,
  rendering selection, PiP fallback, or orientation policy changed.

### Files changed

- `app/src/test/java/com/limelight/ui/SidewaysStreamLayoutTest.java` (new)
- `app/src/test/java/com/limelight/ui/FloatingControlPositionStoreTest.java`
- `PROJECT_STATE.md`

### Persistence / compatibility

No runtime data changes. The existing four position slots remain `portrait`, `landscape`,
`sideways_cw`, and `sideways_ccw`; coverage now ensures a reset clears all of them.

### Lifecycle / race / safety review

- Read and retained the existing `StreamContainer` identity-aware TextureView attach/destroy
  behavior. It is unchanged.
- Read and retained Game's unsupported 3D/external-display fallback, HDR/PiP restrictions, full
  in-stream keyboard fallback, and raw-to-logical drag mapping. They are covered by existing pure
  policy tests where meaningful and unchanged by this patch.
- No native `moonlight-core` load was introduced into Robolectric. MediaCodec/TextureView handoff,
  OEM orientation, IME behavior, and actual decoder Surface replacement remain physical-device
  validation areas.

### Tests actually run

- `./gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "com.limelight.SidewaysStreamModeTest" --tests "com.limelight.ui.SidewaysStreamLayoutTest" --tests "com.limelight.ui.FloatingControlPositionStoreTest" --tests "com.limelight.preferences.BackgroundStreamingPolicyTest" --tests "com.limelight.ArtemisOrientationHelperTest"` — PASS (28 tests)
- `./gradlew.bat :app:assembleNonRoot_gameDebug` — PASS
- `git diff --check` — PASS
- Full inherited unit suite — NOT RUN; it retains documented legacy/native Robolectric baseline
  failures and this coverage-only patch exercised every affected pure/layout suite.

### GitHub Actions / release

- CI result: not yet run; branch has not been pushed at handoff creation.
- APK/release publication: not requested and not performed.
- Signing material/configuration: untouched.

### Known limitations / real-device validation

- Test both CW and CCW on physical Android hardware with actual video: TextureView availability,
  Surface identity/recreation, pointer/editor drag mapping, and full keyboard behavior.
- Exercise Fast Resume and Keep Connection Alive return paths, PiP/manual Rotate blocking, and
  physical portrait system UI on an OEM device. Robolectric cannot establish MediaCodec safety.

### Audit hotspots

- `SidewaysStreamLayout.onMeasure/onLayout`: review the expected centered negative child bounds
  when a landscape logical canvas is rotated into the portrait root.
- `StreamContainer.attachSidewaysTexture()` / destruction callback ordering: verify real vendor
  behavior rather than extrapolating from the layout test.
- `FloatingControlPositionStore.clearAllOrientations()`: ensure future persistence slots are added
  to the reset loop and its regression test together.

### Deferred work

- No speculative Surface lifecycle abstraction or fake native integration test was added.
- Foldable/Diana Phase 5 remains a read-only feasibility audit, independent of this test branch.
- The separate action-profile, snapping, UI/localization, and generic persistence-coverage PRs
  remain unmerged and are not combined here.

### PROJECT_STATE update

Recorded the newly covered transform/reset invariants and clarified that TextureView/MediaCodec
lifecycle validation remains deliberately device-focused.

### Suggested reviewer action

Audit and, if clean, merge. Physical-device testing is recommended for the named native/OEM
boundaries but is not a reason to fabricate a brittle unit test.

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
