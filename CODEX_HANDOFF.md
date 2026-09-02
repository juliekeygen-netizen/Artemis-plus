# Artemis Plus — Codex Review Handoff

This is the rolling review packet for the latest coherent audit state. Always inspect live GitHub state before relying on recorded SHAs.

---

## Current baseline

- Base branch: `main`
- Verified `main`: `e7b338638c069e1648d40e579e19be81bdc323ae`
- Latest merge: `fix: make settings profile storage atomic (#38)`
- Post-merge Android CI #289 (`33637261255`): success
- Post-merge Build Debug APK #125 (`33637261090`): success
- AGP: 8.13.0
- Gradle wrapper: 8.14.2
- Java: 17
- Established APK signer SHA-256: `88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

Current documentation refresh branch:

`maintenance/refresh-audit-state-sep02`

This branch is documentation-only and exists to remove stale audit instructions that would otherwise cause duplicate work.

---

## Recently completed work that must not be repeated

### Build / release

- #20–#22: release/signing workflow hardening and fail-closed `apksigner` certificate parsing.
- #27: GitHub-hosted actions moved to Node-24-capable majors while preserving pinned/proven Android toolchain assumptions.
- #28: stale Android resource warning cleanup.
- #34: seven repository-owned Gradle Groovy property assignment deprecations fixed and re-audited under full warning mode.

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

### Editor geometry

- #30 completed connected long-label expansion using **pre-expansion graph topology**. The full connected follower component, including offset descendants, moves with the expansion while left/below-only branches stay fixed. Older notes saying this remains unresolved are stale.

---

## Current lifecycle audit result

The background-stream implementation is not missing. Current `Game` already contains:

- Fast Resume lifecycle arming/background/return state;
- Keep Connection Alive lifecycle state;
- foreground keep-alive service readiness handling;
- headless decoder surface switching;
- wake-lock timeout handling;
- Keep Alive → Fast Resume fallback;
- PiP exclusions and visible-surface restoration logic.

Treat these as audit surfaces and add narrow regressions when a concrete invariant is found; do not rewrite the subsystem from stale requirements.

### Concrete next defect: Android 11 PiP manual-entry exception handling

Current PiP version split is structurally correct:

- Android O–Q: manual entry in `onUserLeaveHint()`;
- Android R: manual entry in `onPictureInPictureRequested()`;
- Android S+: system auto-enter when enabled.

The O–Q manual path already catches exceptions because some OEMs have thrown from `enterPictureInPictureMode()`. Android R performs the same manual entry without that guard. This is the next coherent lifecycle patch.

Requirements for that patch:

1. preserve the existing Android-version split;
2. preserve external-display/auto-enter semantics;
3. prevent an Android R manual PiP-entry exception from crashing the stream Activity;
4. keep `onPictureInPictureRequested()` callback handling semantics correct;
5. prefer a small testable helper/entry boundary rather than a broad `Game` rewrite;
6. add focused regression coverage and include it in the focused CI gate if needed;
7. verify exact-head compile/focused CI before merge.

The GitHub connector available to the current ChatGPT session exposes full-file replacement but no partial-edit primitive. `Game.java` is large, so avoid reconstructing it unsafely merely to force the patch through a weak write path. If a safe exact-content edit path is available in the next environment/session, use it.

---

## Remaining audit queue after PiP

1. **Persisted-state robustness**
   - `QuickMenuConfig` persistence/version/malformed recovery;
   - `KeyComboManager` and other JSON/SharedPreferences owners;
   - active-reference repair and stale/duplicate IDs;
   - listener/apply-order races;
   - direct non-atomic user-state file writes.

2. **Lifecycle/race coverage**
   - background park/resume and reconnect failure;
   - Keep Alive fallback and delayed teardown ownership;
   - Surface/TextureView restoration ordering;
   - controller detach/reconciliation while parked;
   - delayed UI callbacks after Activity/Fragment teardown.

3. **Repository/security hygiene**
   - generated binaries/build output;
   - machine-local config/path artifacts;
   - secrets/signing material;
   - temporary diagnostic logs/workflow instrumentation;
   - unrelated helper utilities.

4. **UI/localization debt**
   - resource-back user-facing Artemis Action / Quick Menu registry display metadata without changing stable IDs.

5. **Experimental hardware work**
   - Sideways/MediaCodec/PiP/OEM behavior needs real-device validation;
   - Diana audit found no complete cover-screen controller/analog-trigger subsystem to port wholesale; future foldable work must be capability-gated.

---

## Operating rules

- Read and follow `AGENTS.md`.
- Inspect real `main` before creating a branch.
- Keep changes narrow and reviewable.
- Do not mix build-stack upgrades into unrelated fixes.
- Do not weaken tests to obtain green CI.
- Distinguish inherited/environment-specific Robolectric failures from patch regressions.
- Preserve update signing identity.
- Continue autonomously into the next useful audit item after each coherent merge instead of stopping after one patch.
