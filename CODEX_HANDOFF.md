# Artemis Plus — Codex Review Handoff

This is the rolling review packet for the latest coherent Codex task. It is intentionally focused
on the branch/PR below; Git history preserves older packets.

## Task

Diana foldable-feature feasibility audit (documentation-only).

## User goal

Read the current Artemis Plus source and the relevant Diana implementation before deciding whether
to carry forward Diana-inspired foldable ideas: cover-screen triggers, analog-trigger emulation,
and a profile-overlay/cover-screen UX. Do not add speculative hardware code. Publish a focused
documentation/planning PR only if it provides a safe implementation boundary.

## Repository state

- Base branch: `main`
- Base commit: `de32c77770346c7029dcde8011157b3ab302ed13` (`-.`)
- Task branch: `docs/diana-foldable-feasibility`
- Product documentation commit: `53eb90b239a337e0b9190059e04b2e5c89dfb4d4`
- Current head: this handoff commit
- Pull request: [#17 — docs: audit Diana foldable feature feasibility](https://github.com/juliekeygen-netizen/Artemis-plus/pull/17)
- PR state at handoff update: open and unmerged; the product commit's `verify` check was in
  progress when inspected.

## Scope completed

- Added `DIANA_FOLDABLE_FEASIBILITY.md`, a read-only source comparison and implementation plan.
- Updated the unmerged-work wording in `PROJECT_STATE.md` Priority 5 so future work starts from
  the audit instead of repeating the assumption that Diana contains a directly portable feature.
- Did **not** modify Android production code, resources, manifests, Gradle dependencies, tests,
  signing, release configuration, or user data.

## Key implementation decisions

- The direct Diana suite inspection found generic upstream foldable fixes, not a standalone
  cover-screen controller, virtual analog-trigger system, or cover-screen profile UI. No source
  was copied from it.
- Keep `Game`/decoder/surface ownership, `ControllerHandler`, and `OscProfilesManager` as the
  single owners of their existing responsibilities. A future cover UI must not create a second
  stream, controller, or profile-persistence format.
- Treat `ExternalDisplayControlActivity` as external-display support, not as a cover-screen
  abstraction. A foldable can change physical screens while retaining `DEFAULT_DISPLAY`.
- Existing `ControllerHandler` already accepts physical analog trigger axes. Existing on-screen
  triggers are intentionally digital. If requested, an analog OSC control can be evaluated first
  as a device-independent, byte-valued POC at the existing `reportOscState()` seam.
- A posture/cover-screen feature must be capability-gated and opt-in only when available; ordinary
  phone/tablet behavior remains the fallback. The current build has no AndroidX WindowManager
  dependency, so adding one would be a deliberate future decision rather than a missing import.

## Files changed

- `DIANA_FOLDABLE_FEASIBILITY.md` — evidence, reusable seams, POC boundaries, acceptance/device
  matrix, and recommended sequencing.
- `PROJECT_STATE.md` — Priority 5 records the audit result while leaving hardware implementation
  deferred.
- `CODEX_HANDOFF.md` — this review packet.

## Persistence / compatibility

There is no runtime or persistence change. The recommendation explicitly preserves the legacy
OSC working-set compatibility and snapshot recovery owned by `OscProfilesManager`; a future
presentation layer must use that manager rather than introduce cover-specific profiles.

## Lifecycle / race / safety review

The audit checked the paths most likely to make a foldable feature unsafe:

- `Game.onConfigurationChanged()` refreshes OSC and keyboard layouts;
- `StreamSettings` compares display physical pixel counts so screen changes on a shared default
  display reload display-dependent preferences;
- external-display lifecycle is separate from a foldable's default display;
- current sideways-stream, PiP, Fast Resume, Keep Connection Alive, decoder-surface, and
  controller ownership are left untouched.

The document requires future real-device validation for fold/unfold transitions, insets, surface
replacement, input cancel/release, profile persistence, PiP/multi-window, and background-stream
transitions before promoting a POC.

## Tests actually run

- `git diff --check` — **passed** before the product documentation commit.
- Read-only source audit — **completed** against `main`, local
  `origin/feature/diana-osc-port`, and a full-history clone of
  `ZDPepos/diana-oscsuite` at `3397ec7750969466ad8983364ee1a33182bbffa1`.

No Gradle build or unit tests were run: this PR changes documentation only and contains no
application/build/test-source diff. No claim is made about a new Android build or device run.

## GitHub Actions / release

- The PR was created from product commit `53eb90b2`; its `verify` check was **in progress** at the
  last inspection before this handoff update.
- No release/APK was produced or requested for this documentation-only work.
- Do not treat this handoff as confirmation of a post-update CI result; inspect the current PR head
  before merge.

## Known limitations / real-device validation

No foldable hardware behavior was tested because none was implemented. If a POC is approved,
validate closed/cover and open/inner states on at least one real foldable, plus a non-foldable
fallback device, across active streaming, foreground/background, profile editing, trigger cancel,
and relevant decoder/PiP modes.

## Audit hotspots

- Confirm the source comparison correctly distinguishes generic upstream foldable fixes from the
  requested Diana-specific product ideas.
- Review the proposed seams: digital OSC triggers currently send `0xFF`/`0x00`, whereas physical
  analog controller state is already handled independently.
- Reject any later design that conflates a cover display with `ExternalDisplayControlActivity`,
  adds unguarded vendor APIs, or bypasses `OscProfilesManager` persistence/recovery.

## Deferred work

- No hardware or AndroidX WindowManager dependency was added.
- No cover-screen trigger controller, virtual analog trigger, or cover-specific profile overlay
  was implemented.
- If user demand justifies it, start with a separately reviewable analog-OSC POC, then a
  capability-only fold/posture POC, before any visible cover-screen UI.

## Suggested reviewer action

Review PR #17 as a documentation/design-boundary change. Keep it unmerged until the broader
independent audit sequence is ready for promotion; no functional testing is needed solely for this
documentation diff.
