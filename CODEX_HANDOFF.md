# Artemis Plus — Codex Review Handoff

This is the **rolling review packet** for the latest coherent Codex/agent task.

`AGENTS.md` requires this file to be refreshed at the end of every completed task, committed on the task branch, pushed, and included in the pull request. The user can then ask ChatGPT or another reviewer to audit the exact branch/PR without relying on chat memory.

Git history preserves old versions of this file; keep the working copy focused on the latest task.

---

## Current handoff

### Task

Regression coverage gap audit for persisted Quick Menu and OSC profile boundaries.

### User goal

Audit the existing durable regression coverage and add small, focused tests only where current
behavior lacks protection. Avoid production behavior changes and do not conceal inherited test
failures.

### Repository state

- Base branch: `main`
- Base commit: `de32c77770346c7029dcde8011157b3ab302ed13`
- Task branch: `audit/regression-coverage-gap`
- Product commit: `41702e4c8995819d8573dbde03b4b1b013a0a598`
- Current handoff commit: this commit (the PR tip includes this packet)
- Pull request: pending publication
- PR state: local validation complete; ready to push and open for review

### Scope completed

- Added a Quick Menu deserialization regression proving externally persisted/corrupt deep page
  trees stop at `MAX_PAGE_DEPTH` and do not retain descendants/actions below that boundary.
- Added an OSC profile regression proving blank names fall back to the stable generated name and
  oversized names are capped at the existing 80-character persistence boundary.
- Updated durable project state to record both protections.

### Key implementation decisions

- This is test/documentation-only work: no production code, JSON formats, preference keys, or
  lifecycle paths changed.
- Tests exercise the real `QuickMenuConfig.fromJson()` and `OscProfilesManager` public APIs,
  rather than mocking or reaching into their private normalization/parser logic.
- The audit deliberately did not force a `VirtualController` profile-switch integration test,
  because constructing the stream controller loads native Moonlight code under Robolectric. A
  valid future seam would need to preserve real working-set ownership rather than hiding that
  native boundary with a meaningless test.

### Files changed

- `app/src/test/java/com/limelight/quickmenu/QuickMenuConfigTest.java`
- `app/src/test/java/com/limelight/binding/input/virtual_controller/OscProfilesManagerTest.java`
- `PROJECT_STATE.md`

### Persistence / compatibility

No runtime/persistence change. Existing Quick Menu JSON, OSC profile metadata, per-game mappings,
and fallback behavior remain exactly as before; the tests now pin existing depth/name bounds.

### Lifecycle / race / safety review

No Activity, Fragment, stream, Surface, input, background, PiP, callback, or threading behavior
changed. The covered boundaries are defensive persistence paths for malformed/external content and
user-supplied profile names.

### Tests actually run

- `./gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "com.limelight.quickmenu.QuickMenuConfigTest" --tests "com.limelight.binding.input.virtual_controller.OscProfilesManagerTest"` — PASS (21 tests)
- `./gradlew.bat :app:assembleNonRoot_gameDebug` — PASS
- `git diff --check` — PASS
- Full inherited unit suite — NOT RUN; it has documented legacy/native Robolectric baseline
  failures and this test-only change was covered by both affected suites.

### GitHub Actions / release

- CI result: not yet run; branch has not been pushed at handoff creation.
- APK/release publication: not requested and not performed.
- Signing material/configuration: untouched.

### Known limitations / real-device validation

No new device-only behavior was introduced. Existing real-device coverage remains necessary for
stream lifecycle, MediaCodec, OEM orientation, and in-stream UI work; those are outside this
test-only patch.

### Audit hotspots

- `QuickMenuConfig.pageFromJson()`: review the interpretation of `MAX_PAGE_DEPTH` (the root is
  depth zero and up to six nested pages are retained) against the editor's UI depth policy.
- `OscProfilesManager.normalizeName()`: verify the 80-character boundary remains intentional for
  profile UI/metadata consumers.
- Confirm the patch did not accidentally add a test environment seam that changes production
  ownership or suppresses native failures.

### Deferred work

- Stream-controller/profile-switch integration needs a valid non-native test seam before adding a
  Robolectric test; it was not papered over here.
- The independent action-profile, snapping, and UI/localization audit branches remain unmerged
  and are not folded into this coverage-only diff.

### PROJECT_STATE update

Documented direct coverage for Quick Menu maximum depth and OSC profile name normalization.

### Suggested reviewer action

Audit and, if the depth-bound interpretation is correct, merge. No special real-device block is
required for this test-only patch.

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
