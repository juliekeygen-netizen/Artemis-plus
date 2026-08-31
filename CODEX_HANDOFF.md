# Artemis Plus — Codex Review Handoff

This is the **rolling review packet** for the latest coherent Codex/agent task.

`AGENTS.md` requires this file to be refreshed at the end of coherent work so a future reviewer can recover the exact intent, risks, validation, and durable repository state without relying on old chat context.

Git history preserves earlier versions. Keep the working copy focused on the latest task and verify live source/history before acting on any SHA recorded here.

---

## Current handoff

### Task

Continuum independent audit, merge-safety repair, regression strengthening, and post-merge durable-state synchronization.

### Repository state

- Product/audit baseline before this documentation-sync branch: `main` at `9c01289095c5ef65631d794a8b608febebf50347`
- Task branch: `audit/postmerge-state-sync`
- `PROJECT_STATE.md` refresh commit on this branch: `b5c0c5dc394cdf48c027aeb64620fa6dd41dde63`
- Current branch head: the commit containing this handoff or a later audit-only successor; inspect the live branch before review.
- No Android application/build/signing behavior is intentionally changed by this state-sync task.

### Scope completed in the Continuum audit

The independent audit reviewed the parallel work that followed PR #9, corrected unsafe or misleading pieces, normalized merge-conflicting handoff changes out of parallel PRs, reran CI on corrected heads, and merged the clean pieces in dependency-safe order.

Merged audit sequence:

- **#18 — Remove accidental Repo AutoPull artifacts** → `1ceb0b17976f760bb6ad1fd4870a25560ce9713b`
- **#12 — Audit keyboard profile Action bundles** → `9673142c93cf8d3afbe1e8865e774b8ba1b36a58`
- **#14 — Polish Artemis editor dialogs/localization infrastructure** → `58ec2d38132b5d30793c23a0165766bb1590891d`
- **#13 — Make keyboard layout snapping deterministic** → `e4187081d4663ad261354c9fdfe9a1e605e4be56`
- **#15 — Quick Menu/OSC profile boundary regressions** → `4eec6f0d2178e0dac96521cd4c9961ffcc1e63d3`
- **#16 — Sideways-stream layout invariants** → `544c57494661bae5854e38325a67241a67c84666`
- **#17 — Diana foldable feasibility audit** → `9c01289095c5ef65631d794a8b608febebf50347`

### Most important audit findings and corrections

#### 1. Repository contamination was removed

A direct `main` commit (`de32c77770346c7029dcde8011157b3ab302ed13`) accidentally added nine RepoAutoPull artifacts unrelated to Artemis Plus, including generated Windows executables/shortcuts, a helper script/readme, and config containing a machine-local absolute repository path.

PR #18 was verified as the exact inverse of that commit and removed exactly those nine paths without touching Artemis application/build/signing code. A later root-tree check found no RepoAutoPull leftovers.

#### 2. Artemis Action profile import/export was already substantially implemented

The audit confirmed the modern `artemis-plus-keyboard-profiles` format already round-trips profile layout, custom-key definitions, and Artemis Action selections. The stale README claim that Action support was still wholly planned was corrected.

#12 also hardened compatibility:

- unknown/future Action IDs survive older-client import/export inertly;
- unknown IDs are never instantiated/executed by a build that does not know them;
- editing known Actions preserves opaque IDs;
- known Action export order is deterministic, followed by sorted unknown IDs;
- unsupported modern bundle format/version is rejected;
- modern bundle structure is validated before profile writes;
- legacy single-layout geometry import remains compatible and intentionally geometry-only.

A deeper audit traced the downstream key/action importers before accepting the partial-write claim.

#### 3. An unsafe snapping/text-expansion implementation was removed before merge

The original #13 branch attempted to shift a connected control group when a long custom-key label widened. Audit found a topology defect: a direct right-edge neighbor could move while an offset descendant connected through it stayed behind because the descendant's own X coordinate failed a simple threshold test.

The unsafe controller/text-expansion behavior was removed completely instead of shipping a known-bad fix. `KeyBoardController.java` remained on the safe baseline for that behavior.

What did merge from #13:

- deterministic tie-breaking for equal-score snap candidates;
- mixed-size/no-resize, center, gap, nearest-candidate, grouping and hysteresis regressions;
- a pure helper exposing the existing sticky-axis release comparison for tests without changing the release rule.

Future connected long-label expansion must use **pre-expansion graph traversal**, not the rejected threshold-only translation. See `PROJECT_STATE.md` for the required topology/regression shape.

#### 4. Parallel #12/#14 Action-picker overlap was neutralized

#12 and #14 originally changed the same Action-picker file from the same old base. During audit, #14's `ArtemisActionButtonFactory.java` was replaced with the audited #12 version before #12 merged. After #12 merged, the #14 branch file was byte-identical to `main`, preventing #14 from accidentally undoing forward-compatible unknown-ID behavior.

#### 5. Localization scope was corrected

#14 improved `ArtemisEditorUi` consistency, resource-backed shell strings/plurals, and compact-dialog scrolling. It does **not** finish Artemis Plus localization.

Two important display catalogs remain hard-coded English:

- `ArtemisAction` labels;
- `StreamActionRegistry` labels/categories/descriptions.

Stable IDs are persistence/runtime contracts and must never be translated or renamed as part of display localization.

#### 6. Sideways stream coverage improved, native validation remains

#16 added layout-level regression coverage without changing production behavior. Physical-device validation is still required for TextureView/MediaCodec Surface transitions, CW/CCW input transforms, PiP/fallback UX, IME/system-window behavior, and Keep Alive handoff.

#### 7. Diana foldable work is not a hidden feature to port wholesale

The independent audit checked cited Diana history, including foldable/configuration compatibility commits, and found no reusable full cover-screen controller, virtual analog-trigger subsystem, or cover-profile UI. `DIANA_FOLDABLE_FEASIBILITY.md` records the evidence and proposes capability-gated POC boundaries.

### Tests and CI actually observed

Corrected audit PR heads were green in Android CI before merge, including the narrowed #13 snapping head and cross-PR-safe #14 head. #15/#16 regression-only heads and #17 documentation head also had successful CI in the audit session.

Important precision: immediate checks of some squash-merge commit SHAs did **not** expose a separate post-merge workflow run at that moment. Do not claim every squash merge itself had a new CI run unless current Actions history proves it.

No signing material was rotated during the audit.

### Current unresolved / high-risk areas

- connected long-label expansion across mixed-size/offset control topology;
- final resource-backed display metadata for Action and Quick Menu registries;
- real-device Sideways/TextureView/MediaCodec behavior;
- Keep Alive / Fast Resume / Surface/controller lifecycle races that Robolectric cannot fully model;
- release/signing/workflow drift and whether rolling `debug-latest` still targets current `main` after the audit merges;
- repository hygiene after the accidental RepoAutoPull direct commit.

### Next audit priority

Continue in audit mode with **release/signing/workflow drift** first:

- inspect `.github/workflows/` triggers and gates;
- determine intended PR vs `main` post-merge CI behavior;
- inspect rolling `debug-latest` publication flow and current tag/release target;
- compare `SIGNING.md` to actual workflow behavior;
- verify signer-check/checksum mechanisms;
- scan tracked source for signing secrets, generated binaries, machine-local paths, temporary diagnostics and release outputs.

Read-only first. Do not regenerate or rotate signing identity.

After that, audit persisted-state robustness and lifecycle/race regressions as prioritized in `PROJECT_STATE.md`.

### Stable signing invariant

Previously verified certificate SHA-256:

`88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

Rolling prerelease tag:

`debug-latest`

Never expose signing secrets or replace the established signer casually.

### Suggested reviewer action

Audit this state-sync branch against current source/history. If the two durable documents accurately describe the merged state and no unrelated file changed, merge it. Then continue the Continuum audit from the release/signing/workflow priority rather than returning to normal feature implementation.

---

# Required template for future coherent tasks

Replace the **Current handoff** section above with concrete information for each completed coherent task. Do not leave placeholders in a completed handoff.

## Task

Short feature/fix/audit name.

## User goal

Describe the requested user-visible behavior and important constraints.

## Repository state

- Base branch / commit
- Task branch
- Current head or durable task checkpoint
- Pull request number/URL and state

## Scope completed

Explain exactly what was implemented/fixed/audited. Group by subsystem when useful.

## Key implementation decisions

Record what a reviewer needs to know about state ownership, lifecycle timing, compatibility/migration, fallbacks, malformed/stale data, and why existing architecture was reused rather than duplicated.

## Files changed

List materially changed paths. Remove unrelated/generated/temp files before handoff.

## Persistence / compatibility

State what existing preferences/layouts/profiles/import formats continue to work and how migrations or repairs behave.

## Lifecycle / race / safety review

Include only relevant checks: recreation, configuration, PiP/multi-window, background/foreground, Surface/TextureView, controller/input suspend-resume, delayed callbacks/teardown, idempotence, corrupt SharedPreferences, performance/UI-thread concerns.

## Tests actually run

State exact commands/suites and real outcomes. If not run, say `NOT RUN` and why.

## GitHub Actions / release

Record workflow/run result, APK publication, `debug-latest`, and signing checks only if actually observed.

## Known limitations / real-device validation

Name anything CI cannot prove, especially OEM orientation, MediaCodec Surface switching, PiP appearance, touch transforms, foreground-service/battery behavior, and hardware-specific features.

## Audit hotspots

Name files/functions/state transitions where failure is most plausible and the invariants to check.

## Deferred work

List intentionally deferred items and why they were not mixed into the patch.

## PROJECT_STATE update

Explain what durable state changed, or explicitly state that none was required.

## Suggested reviewer action

Use one of: audit/merge if clean; audit named risks; real-device test before merge; further implementation required before review.
