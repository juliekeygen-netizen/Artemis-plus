# Artemis Plus — Codex Review Handoff

This is the rolling review packet for the latest coherent audit state. Always inspect live GitHub state before relying on recorded SHAs.

---

## Current baseline

- Base branch: `main`
- Verified `main`: `47c58451186d8c6b55811d9f7a193a740e996f08`
- Latest merge: `Reject keyboard profiles that alias the same storage (#51)`
- Post-merge Android CI run `33676129443`: success
- AGP: 8.13.0
- Gradle wrapper: 8.14.2
- Java: 17
- NDK: 27.0.12077973
- Android platform: 36
- Established APK signer SHA-256: `88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

Current documentation refresh branch:

`maintenance/refresh-audit-state-post51`

This branch is documentation-only and records the completed persistence/PiP hardening sequence through #51 so stale handoffs do not trigger duplicate work.

---

## Recently completed work that must not be repeated

### Build / release

- #20–#22: release/signing workflow hardening and fail-closed, fixture-tested `apksigner` certificate parsing.
- #27: GitHub-hosted actions moved to Node-24-capable majors while preserving pinned/proven Android toolchain assumptions.
- #28: stale Android resource warning cleanup.
- #34: repository-owned Gradle Groovy property assignment deprecations fixed and re-audited.

The debuggable + `minifyEnabled true` warning was separately reviewed. Do not disable shrinking solely to silence that warning.

### Persistence / corruption recovery

- #23: unknown future Quick Menu action IDs preserved inertly.
- #25: OSC profile metadata recovery preserves valid siblings and repairs referenced damaged state.
- #29: keyboard/profile wrong-type and partial-malformation recovery.
- #32: malformed/non-finite floating-control position recovery.
- #33: malformed outside-stream orientation preference recovery.
- #35: malformed floating-reset preference recovery at stream startup.
- #37: profile overlay typed-getter recovery, including Gson list → string-set normalization.
- #38: `profiles.json` atomic writes, serialized file access, transactional reload, interrupted-write and malformed-commit regressions.
- #40: Quick Menu wrong-type preference recovery.
- #42: base/default preference reads protected even when no settings profile is active.
- #43: malformed string-backed preference recovery.
- #44: mutation-safe settings-profile listener dispatch.
- #45: duplicate settings-profile UUID rejection during transactional recovery.
- #48: profile-editor Gson `Double`/`List` compatibility and rotation/saved-state preservation.
- #49: wrong-type recovery for the separate named `GlPreferences` store.
- #50: normal/global Settings routed through a recovering base SharedPreferences/`PreferenceDataStore` adapter before pre-reads and XML inflation, while the profile editor keeps its in-memory store.
- #51: duplicate keyboard profile backing-storage aliases rejected; the first valid owner and its backing data survive, and stale active selection is repaired.

### PiP / lifecycle

- #41: Android 11 manual PiP entry is exception-guarded while preserving the existing O–Q manual and Android 12+ auto-enter split.

### Editor geometry

- #30 completed connected long-label expansion using **pre-expansion graph topology**. The full connected follower component, including offset descendants, moves with expansion while left/below-only branches stay fixed. Older notes saying this remains unresolved are stale.

---

## Current persistence audit result

The principal Artemis-owned persistence boundaries have now been audited and/or hardened:

- settings profiles and base/default overlays;
- profile-editor Gson/saved-state boundary;
- Quick Menu config;
- OSC profile metadata;
- keyboard profile metadata, key-combo definitions, and Artemis Action selections;
- GL renderer/fingerprint preferences;
- floating-control positions/reset behavior;
- outside-stream orientation state.

The post-#51 sweep also re-read `ArtemisActionButtonFactory`: selection reads already use the keyboard-safe raw-map helpers, malformed per-button geometry is discarded, and its writes use copied sets. `ArtemisActionStateReader` is runtime-only and does not own persisted state. No additional reproducible corruption defect was established.

Do not continue a generic persistence-hardening campaign by speculation. Re-open a store only when a concrete invariant or failing regression demonstrates a bug.

### Deliberately unresolved keyboard recovery edge

If the entire keyboard-profile metadata blob is destroyed, inactive geometry-only dynamic backing stores can become unreachable. The active dynamic store is preserved. Key/action metadata can identify some inactive stores, but not geometry-only stores, so there is no authoritative complete ownership signal.

Do **not** implement heuristic SharedPreferences scanning that could resurrect stale, cleared, orphaned, partial-import, or unrelated files. Safe recovery needs an explicit ownership marker/migration design first.

---

## Current lifecycle audit state

The background-stream implementation is not missing. Current `Game` already contains:

- Fast Resume lifecycle arming/background/return state;
- Keep Connection Alive lifecycle state;
- foreground keep-alive service readiness handling;
- headless decoder surface switching;
- wake-lock timeout handling;
- Keep Alive → Fast Resume fallback;
- PiP exclusions and visible-surface restoration logic.

The previously documented Android 11 PiP exception gap is **closed by #41**. Do not redo it.

Treat the remaining lifecycle work as evidence-driven audit/test surfaces. High-value targets are:

1. background park/resume and reconnect failure;
2. Keep Alive fallback and delayed teardown ownership;
3. Surface/TextureView restoration ordering;
4. controller detach/reconciliation while parked;
5. delayed UI/lifecycle callbacks after Activity teardown.

Prefer small testable state/ownership helpers over broad `Game` rewrites when a concrete defect is found.

---

## Remaining audit queue

1. **Lifecycle/race coverage**
   - background park/resume and reconnect failure;
   - Keep Alive fallback and delayed teardown ownership;
   - Surface/TextureView restoration ordering;
   - controller detach/reconciliation while parked;
   - delayed callbacks after teardown.

2. **Repository/security hygiene**
   - generated binaries/build output;
   - machine-local config/path artifacts;
   - secrets/signing material;
   - temporary diagnostic logs/workflow instrumentation;
   - unrelated helper utilities.

3. **UI/localization debt**
   - resource-back user-facing Artemis Action / Quick Menu registry labels/categories/descriptions without changing stable IDs.

4. **Hardware-sensitive validation**
   - MediaCodec output-surface switching;
   - TextureView restoration timing;
   - PiP enter/exit on target OEMs;
   - Sideways CW/CCW video/input transforms;
   - foldable/cover-screen behavior.

The Diana audit found no complete cover-screen controller/analog-trigger subsystem to port wholesale. Future foldable work must remain capability-gated.

---

## Merge/verification discipline established during this audit

For code changes:

1. inspect exact branch head and final diff;
2. require exact-head push CI with compile + focused regressions (and full inherited suite when available);
3. open a normal non-draft PR;
4. inspect PR-rendered diff/head/mergeability;
5. require separate PR-triggered exact-head CI;
6. squash merge with `expected_head_sha`;
7. fetch actual `main` after merge.

Temporary branch-only exact-patch scripts/workflows are acceptable for large-file connector edits only when removed from the final tree and squash-merged so helper history never enters `main`.

Do not reuse the accidentally closed placeholder PRs #46/#47.

---

## Operating rules

- Read and follow `AGENTS.md`.
- Inspect real `main` before creating a branch.
- Keep changes narrow and reviewable.
- Do not mix build-stack upgrades into unrelated fixes.
- Do not weaken tests to obtain green CI.
- Distinguish inherited/environment-specific Robolectric failures from patch regressions.
- Preserve update signing identity.
- Do not turn theoretical edge cases into risky recovery heuristics.
- Continue autonomously into the next useful audit item after each coherent merge instead of stopping after one patch.
