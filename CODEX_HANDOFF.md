# Artemis Plus — Codex Review Handoff

This is the rolling review packet for the latest coherent audit state. Always inspect live GitHub state before relying on recorded SHAs.

---

## Current baseline

- Base branch: `main`
- Verified `main`: `7b4dfd5ee878f3e85bcba21cd2fadbad0eecc458`
- Latest merge: `Own controller rumble across reconnect suspension (#75)`
- Post-merge Android CI run `33975853435`: success, including compile, focused Artemis regressions, and the full inherited unit suite
- Post-merge Build Debug APK run `33975853411`: signed package build/verification succeeded; rolling publication was finishing when this handoff refresh began
- AGP: 8.13.0
- Gradle wrapper: 8.14.2
- Java: 17
- NDK: 27.0.12077973
- Android platform: 36
- Established APK signer SHA-256: `88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

Current documentation refresh branch:

`maintenance/refresh-audit-state-post75`

This branch is documentation-only. It supersedes the old post-#51 handoff and records the lifecycle/race audit through #75.

---

## Recently completed work that must not be repeated

### Persistence / build baseline through #52

The earlier persistence/build sequence remains complete: release/signing hardening (#20–#22), future Quick Menu ID preservation (#23), OSC recovery (#25), Node-24 Actions (#27), resource/build warning cleanup (#28/#34), keyboard/profile persistence recovery (#29/#51), editor geometry/floating/orientation recovery (#30/#32/#33/#35), settings/profile hardening (#37–#50), and the post-#51 documentation refresh (#52).

Do not restart a generic persistence-hardening campaign. Re-open a store only when a concrete invariant/failing regression demonstrates a defect.

### Current lifecycle/race hardening sequence

- **#53** — modifier-only custom keys.
- **#54** — smart reconnect owned across `Game` lifecycle.
- **#55** — `NvConnection` start/stop ownership and native-bridge permit handling.
- **#56** — commit-text work stopped during teardown.
- **#57** — late stream-surface callbacks ignored after teardown.
- **#58** — delayed key releases bound to their connection.
- **#60/#61** — digital and non-digital keyboard state released when controls detach.
- **#62** — Wi-Fi monitor lifecycle ownership.
- **#63** — native pointer-capture lifecycle ownership.
- **#64** — delayed `Game` callbacks owned/cancelled across teardown.
- **#65** — stale input re-grab prevention.
- **#66** — connection callbacks owned across `Game` lifecycle/generations.
- **#67** — clipboard workers owned across lifecycle.
- **#68** — stop worker snapshots transport state, suppresses stale UI completion, preserves destroy-time Keep Alive cleanup.
- **#70** — delayed USB callbacks owned across `UsbDriverService` lifecycle.
- **#71** — already-running controller battery polls cannot resurrect after context destruction.
- **#72** — battery polling is sealed/drained across Fast Resume / Keep Alive suspension, with final sends serialized against suspension/teardown.
- **#73** — Android controller sensor registration/final sends are serialized against suspension, while host report-rate requests received during Keep Alive are retained for resume.
- **#74** — recurring mouse-emulation callbacks are stopped across suspension, synthetic mouse buttons released, stale transient axes/buttons neutralized, and active mode resumed safely.
- **#75** — transient Android/Shield/USB rumble is cancelled and blocked across suspension/final stop without stale replay after resume.

All of #72–#75 used CRLF-preserving staging transforms where needed, then clean one-commit branches were reconstructed directly from current `main`. Temporary staging helpers/workflows were not merged. Their staging refs have been repointed to the clean #75 baseline.

---

## Current lifecycle audit result

The old handoff’s broad delayed-callback/controller queue is now largely closed by evidence-driven fixes.

### `ControllerHandler` delayed/output ownership

The current delayed-work inventory is:

- Select+L1 stats hold — cancelled on suspension;
- battery poll loop — owned by #71/#72;
- delayed sensor re-enable — owned by #73;
- mouse-emulation 50 ms loop — owned by #74.

Transient rumble output is separately owned by #75.

Controller RGB LED handling was reviewed and deliberately left stateful. Keep Alive preserves the live host generation; `LightsSession` is migrated with input context ownership and closed on destroy. Android applies requests against the current device light list, so there is not enough evidence for speculative LED suspend/cache/replay logic. Re-open only if hardware testing demonstrates a failure.

### `Game` / reconnect ownership

A post-#75 source re-check found no new reproducible defect in the current Keep Alive → Fast Resume fallback or visible-surface restoration paths:

- failed headless output switching downgrades before controller suspension;
- failed visible-surface restoration deliberately stops the live transport and transfers ownership to the hardened Fast Resume reconnect path;
- Activity teardown invalidates callback ownership and clears its handler queue;
- smart reconnect, clipboard workers, stop workers, connection callbacks, commit text, send-key delayed releases, and surface callbacks have dedicated ownership/cancellation from the recent PR sequence.

Do not interpret this as proof on every OEM/device. Remaining risk is now primarily physical/hardware-sensitive validation rather than an invitation for broad source rewrites.

---

## Repository/security hygiene result

At the #75 baseline, the queued hygiene sweep found no actionable leak:

- APK/AAB/build output, local SDK config, `key/`, and `.artemis-signing/` are ignored;
- repository search found no committed private-key marker or signing password/keystore literal;
- recursive tracked-tree review found no `.exe` or `.jks` artifact and no controller staging patcher/workflow on `main`;
- the tracked `.tflite` file is an intentional application model asset;
- battery/sensor/mouse/rumble disposable staging refs were repointed to clean `main` after use.

Continue to preserve the established signer and never commit credentials/signing material.

---

## Persisted-state conclusion that still matters

If the entire keyboard-profile metadata blob is destroyed, inactive **geometry-only** dynamic backing stores can become unreachable. The active dynamic store is preserved. Key/action metadata can identify some inactive stores, but not geometry-only stores, so there is no authoritative complete ownership signal.

Do **not** implement heuristic SharedPreferences scanning that could resurrect stale, cleared, orphaned, partial-import, or unrelated files. Safe recovery needs an explicit ownership marker/migration design first.

---

## Remaining queue

### 1. Real-device lifecycle acceptance

Highest-value next validation is physical-device testing, especially:

- normal stream start/stop/reconnect;
- Fast Resume within/after timeout;
- Keep Alive supported and fallback paths;
- headless Surface switch and visible Surface restoration;
- background/foreground with controller disconnect/reconnect;
- battery/sensor/mouse-emulation/rumble ownership through background transitions;
- PiP enter/exit on target OEMs;
- Sideways CW/CCW video/input transforms;
- IME/system-window behavior.

Turn a reproducible physical failure into a narrow regression/fix. Do not make speculative lifecycle changes merely because an old handoff lists the area.

### 2. UI/localization debt

Artemis Action / Quick Menu user-facing registry labels, categories, and descriptions still include hard-coded English. Move display metadata to resources while keeping all stable persisted/runtime IDs exactly unchanged.

### 3. Foldable/Diana follow-up

The Diana audit found no complete reusable cover-screen controller/analog-trigger subsystem to port wholesale. Any future work must be capability-gated and designed around actual foldable/posture behavior.

### 4. Targeted performance/ownership work

Continue only when profiling, hardware testing, or a concrete invariant identifies a real issue. The generic delayed-callback and repository-hygiene sweeps are no longer open-ended priorities.

---

## Real-device controller acceptance after #72–#75

On a physical Android device with a real controller, explicitly verify:

1. battery polling stops while backgrounded and restarts once after return;
2. sensor listeners stop and do not send while suspended, then restore the current host-requested report rates;
3. mouse emulation releases synthetic buttons, stops moving/scrolling in background, and does not replay stale axis/button state on return;
4. Android/Shield/USB rumble stops immediately on background and is not replayed after resume;
5. controller removal while backgrounded is reconciled before resumed input;
6. RGB LED state remains sensible across Keep Alive/background removal on target hardware.

Do not classify total input failure as a client regression until Apollo per-client input/launch permissions are checked.

---

## Merge/verification discipline

For code changes:

1. inspect exact branch head and final diff;
2. require exact-head push CI with compile + focused regressions and the full inherited suite when available;
3. open a normal non-draft PR;
4. inspect PR head/base/mergeability;
5. require separate PR-triggered exact-head CI;
6. squash merge with `expected_head_sha`;
7. fetch actual `main` after merge;
8. let post-merge Android CI / signed build workflows validate the durable baseline.

Temporary branch-only exact-patch scripts/workflows are acceptable for large CRLF-sensitive connector edits only when excluded from the final tree and cleaned from staging refs afterward.

---

## Operating rules

- Read and follow `AGENTS.md`.
- Inspect real `main` before creating a branch.
- Keep changes narrow and reviewable.
- Do not mix build-stack upgrades into unrelated fixes.
- Do not weaken tests to obtain green CI.
- Preserve CRLF/file modes where intentional.
- Distinguish inherited/environment-specific failures from patch regressions.
- Preserve update signing identity.
- Do not turn theoretical edge cases into risky recovery heuristics.
- Continue autonomously into the next useful audit item after each coherent merge instead of stopping after one patch.
