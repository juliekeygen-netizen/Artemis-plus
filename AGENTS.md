# Artemis Plus — Agent Operating Instructions

These instructions apply to the whole repository. They are written for Codex and other coding agents working locally in this checkout.

## 1. Sources of truth and startup sequence

At the start of every substantial task:

1. Read this `AGENTS.md`.
2. Read `PROJECT_STATE.md` for the current durable project state, architecture notes, priorities, known weak spots, and next recommended work.
3. Read `CODEX_HANDOFF.md` if it contains a still-active review handoff from a previous task.
4. Inspect the real Git state with `git status`, current branch, recent history, and configured remotes.
5. Synchronize from the configured `origin` when network access is available. Do not hard-code or depend on a repository URL from these instructions; use the checkout's configured Git remote.
6. Inspect the current source, tests, build files, and relevant history before deciding what is actually missing.

Source code and tests are authoritative. `PROJECT_STATE.md` is a maintained development handoff/roadmap, not permission to ignore newer code. If documentation and source disagree, investigate and update the stale documentation as part of the task.

Do not reimplement a feature merely because an old handoff or README calls it planned. This project moves quickly and old TODOs can become stale.

## 2. Default Git workflow: always leave reviewable durable work

For every coherent implementation, bug fix, audit-fix batch, or documentation phase:

1. Start from an up-to-date `main` unless the user explicitly asked to continue an existing branch.
2. Create a descriptive branch such as `feature/...`, `fix/...`, `audit/...`, or `docs/...`.
3. Implement and validate the coherent task on that branch.
4. Commit all intended changes with clear commit messages.
5. Push the branch to `origin` when credentials/network access permit.
6. Open or update a pull request targeting `main` when GitHub access permits.
7. Do **not** merge the PR by default. Leave the completed task published as a reviewable remote branch/PR so the user can ask ChatGPT or another reviewer to audit the exact durable diff.
8. Only merge when the user explicitly asks for merge/promotion, or when the active task explicitly states that autonomous merge after successful review/CI is desired.

A task is not considered fully handed off if the only copy of the changes exists in an uncommitted local working tree.

If pushing/opening a PR is temporarily blocked, still finish local validation, commit the coherent work, update `CODEX_HANDOFF.md`, and clearly report the exact blocker. Retry publishing if the blocker can be resolved without user input.

Never force-push or rewrite `main`. Avoid destructive commands such as `git reset --hard`, `git clean -fd`, or discarding unknown user changes unless explicitly required and approved.

## 3. Mandatory end-of-task review packet

At the end of every coherent task, before declaring it complete, update `CODEX_HANDOFF.md` on the task branch and commit that update.

The handoff must contain:

- task/feature name;
- branch name;
- base commit and current head commit;
- PR number/URL if available;
- concise intent and user-visible behavior;
- implementation summary by subsystem;
- complete list of materially changed files or grouped paths;
- important architecture/state-ownership decisions;
- backward-compatibility/migration behavior;
- lifecycle/race/persistence/performance considerations checked;
- tests and build commands actually run, with pass/fail results;
- CI workflow/run state if available;
- release/APK state when relevant;
- known limitations and real-device tests still required;
- audit hotspots: what a reviewer should inspect most carefully;
- any remaining work intentionally deferred.

Do not write "tests pass" unless they were actually run. Distinguish local validation from GitHub Actions and from real-device validation.

`CODEX_HANDOFF.md` is a rolling review packet. Git history preserves older versions, so keep the current file focused on the latest completed or in-review task.

## 4. Keep `PROJECT_STATE.md` current

Whenever a task materially changes the project state, update `PROJECT_STATE.md` in the same branch:

- move completed roadmap items into completed state;
- remove or correct stale claims;
- record important architecture discoveries that future agents should not have to rediscover;
- record new verified bugs/limitations/test gaps;
- reprioritize the next recommended task when appropriate;
- update the durable baseline only after a change is actually merged to `main`.

Do not turn `PROJECT_STATE.md` into a raw diary. Preserve durable technical knowledge, current priorities, and decision rationale.

## 5. Implementation and audit standard

Before editing, understand the owner of the state being changed. Prefer extending the existing architecture over adding a parallel state system.

For substantial work, explicitly check:

- implementation completeness against the requested behavior;
- lifecycle and load-order behavior;
- Activity/Fragment/View recreation;
- configuration/orientation changes;
- PiP and multi-window where relevant;
- background/foreground streaming transitions;
- Surface/TextureView/decoder ownership;
- input suspension/restoration and controller lifecycle;
- delayed callbacks and teardown races;
- SharedPreferences persistence, migration, stale/corrupt data, and recovery;
- backward compatibility with existing Artemis formats/settings;
- performance and avoidable UI/GC churn;
- UI consistency, spacing, hierarchy, touch targets, clipping, and small/portrait layouts;
- tests that prove the real regression rather than only a convenient helper;
- build/CI/release impact;
- accidental unrelated diff or generated files.

When an explicit audit is requested, remain in audit mode until the coherent audit is complete: inspect the real durable code, find problems, fix safe issues, strengthen regression coverage, and revalidate. Do not stop at the first passing test if meaningful independent audit work remains.

If one CI job, external check, or device-only item is pending, continue other useful independent work rather than idling.

## 6. Android-specific caution areas

Artemis Plus is a streaming client, so seemingly small UI changes can interact with stream lifecycle and input ownership. Be particularly careful around:

- `Game` Activity lifecycle and termination/reconnect paths;
- Surface/TextureView creation, destruction, replacement, and MediaCodec output-surface switching;
- Fast Resume and Keep Connection Alive state transitions;
- PiP/visible multi-window/external-display behavior;
- physical Android orientation versus logical sideways-stream orientation;
- raw Android coordinates versus logical stream coordinates;
- keyboard/IME behavior;
- controller, USB, sensor, touchpad, mouse, and custom-overlay input suppression/restoration;
- overlay visibility snapshots/restoration;
- floating-control position persistence across orientation and sessions.

Do not assume emulator/standard Android orientation behavior exactly matches OEM behavior. Device validation may still be required even when unit/CI tests are green.

## 7. Artemis Plus UI direction

Prefer the shared Artemis Plus UI system over new raw platform-default dialogs/widgets when practical. Reuse `ArtemisEditorUi` and existing compact dark UI conventions instead of creating another isolated style.

The user has repeatedly requested:

- consistent compact dark surfaces;
- consistent title/text hierarchy;
- consistent padding and row heights;
- compact menus/popups that flip above an anchor when needed;
- clear active/selected state without redundant text;
- controls that remain usable on smaller/portrait layouts;
- no overlaps, clipping, giant default Android dialogs, or inconsistent visual systems.

Preserve accessibility/touch-target practicality while improving visual compactness.

## 8. Build and test rules

Use the repository's Gradle wrapper and existing CI conventions.

Do not casually upgrade Gradle, Android Gradle Plugin, SDK/NDK, Java, dependencies, or unrelated build tooling while implementing another feature.

Do not weaken, delete, skip, or rewrite regression tests merely to make CI green. If a Robolectric test fails because it tries to load native Moonlight code, determine whether the test design is invalid for the unit environment and replace it with a valid regression seam rather than hiding the failure.

The main verification target is the non-root debug variant. Use focused regression tests during iteration and broader compile/test/APK validation before handoff when practical.

The inherited test suite has known baseline failures documented in `README.md`; do not misattribute those to a new patch without reproducing/comparing the baseline.

Remove temporary validator scripts/workflows/debug files from the final product diff unless they are intentionally becoming permanent tooling.

## 9. Signing and release safety

Do not regenerate, replace, expose, or casually modify the established Artemis Plus signing identity or secrets.

The verified stable signing certificate SHA-256 is:

`88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

If signing/release infrastructure appears broken, investigate before changing key material. A missing `.artemis-signing` directory means restore the private backup; it is **not** permission to generate a new key. `setup-signing.ps1` is expected to verify the established certificate before it can upload signing values to GitHub.

The rolling `debug-latest` prerelease is a privileged `main`-only path. Audit/feature branches should use normal Android CI and must not become routine consumers of the persistent signing secrets or a write-scoped release token.

The release workflow must fail closed if the reconstructed keystore or any produced APK does not match the established certificate fingerprint. Keep build/token permissions least-privileged and isolate `contents: write` to publication after build/signature validation.

Repository-level signing secrets are not the strongest isolation boundary for same-repository workflows. `SIGNING.md` documents the intended future migration to a protected main-only GitHub Actions environment; do not switch to environment-only secret references until the existing values have actually been migrated from the private backup.

Do not change signing/release workflows unless the active task actually requires it, and verify the real `debug-latest` tag/assets after any such merge.

## 10. Important diagnostic trap: Apollo permissions

If video streaming works but **all** mouse/keyboard/touch/controller input appears dead, check Apollo paired-client permissions before concluding the Artemis input pipeline regressed. Reinstall/re-pair can create a client that can view a stream while Mouse Input, Keyboard Input, Touch Input, Controller Input, or Launch Apps permissions are disabled. This has previously produced both dead input and HTTP 403 launch failures.

## 11. Current roadmap discipline

Read the live priorities in `PROJECT_STATE.md` rather than assuming an order from README or an older handoff.

The Artemis Action/profile-bundle import/export investigation is already resolved and merged: the modern profile bundle carries layout + custom keys + Action selections, including inert preservation of unknown/future Action IDs. Do not restart that work unless a genuinely different user-facing interchange requirement is demonstrated.

For experimental/high-risk features, prefer a contained proof-of-concept branch with explicit fallbacks and tests instead of destabilizing the normal path.

## 12. Communication and autonomy

The user prefers agents to do the implementation/audit work rather than repeatedly ask about ordinary engineering choices.

For broad tasks:

- inspect first;
- make reasonable architecture decisions from the repo and requested behavior;
- implement in coherent phases;
- test each phase;
- continue useful independent work while external checks run;
- stop only for genuine user input, a hard safety/tooling limit, or when the requested coherent work is complete.

Progress reports should be concrete: what was inspected, what was found, what changed, what passed, what remains, and where the reviewable branch/PR is.
