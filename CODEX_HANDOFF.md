# Artemis Plus — Codex Review Handoff

This is the **rolling review packet** for the latest coherent Codex/agent task.

`AGENTS.md` requires this file to be refreshed at the end of every completed task, committed on the task branch, pushed, and included in the pull request. The user can then ask ChatGPT or another reviewer to audit the exact branch/PR without relying on chat memory.

Git history preserves old versions of this file; keep the working copy focused on the latest task.

---

## Current handoff

### Task

Set up durable repository context and a Codex-to-reviewer workflow.

### Branch

`docs/codex-project-handoff`

### Base

`main` at `cc136900b15e00df3a62cf2f6d0d70d7c365d3bf`

### Intent

Make future local Codex work self-contained and reviewable by storing:

- stable agent operating rules in `AGENTS.md`;
- current completed phases, architecture decisions, active investigation, known limitations, and prioritized roadmap in `PROJECT_STATE.md`;
- a mandatory rolling review packet in this file.

### Files changed

- `AGENTS.md`
- `PROJECT_STATE.md`
- `CODEX_HANDOFF.md`

### Important workflow decision

Future coding tasks should normally be completed on a pushed feature/fix/audit branch and exposed through a pull request targeting `main`, but **left unmerged by default**. This gives the user a durable exact diff to hand to ChatGPT for an independent audit before promotion.

A task is not considered fully handed off if the only copy exists as uncommitted local changes.

### Validation

Documentation-only setup. Review should verify that the docs accurately reflect current `main` and do not accidentally describe already-completed phases as future work.

### Reviewer focus

- Does `PROJECT_STATE.md` match current source/history through PR #9?
- Does the active import/export investigation correctly recognize that modern keyboard-profile bundles already contain an `actions` array?
- Are future Codex tasks required to leave a durable remote branch/PR and an explicit audit packet?
- Are signing/lifecycle/Apollo diagnostic invariants preserved?

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
