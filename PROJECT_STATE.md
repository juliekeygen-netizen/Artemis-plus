# Artemis Plus — Durable Project State and Roadmap

**Last refreshed:** 2026-09-01  
**Purpose:** durable current-state handoff for Codex/ChatGPT and future contributors.  
**Rule:** source code, tests, current Git history, and current Actions/Release state are authoritative. Verify live `main` before acting; this file is a recovery map, not a substitute for inspection.

---

## 1. Project direction

Artemis Plus is an Android streaming client derived from the newer Marssvoodoo Artemis base. The project keeps that newer streaming/reliability base while selectively porting or rethinking useful Diana OSC ideas and adding Artemis-specific quality-of-life features.

Current product direction centers on:

- advanced on-screen controller and custom-key editing;
- named custom key/chord buttons and local Artemis Action buttons;
- keyboard/control profiles and gamepad OSC profiles;
- per-game automatic OSC profile selection;
- deterministic snapping/grouping/resizing behavior;
- a shared compact Artemis editor/menu UI;
- customizable nested Quick Menu actions/pages;
- orientation, PiP, background-stream and resume hardening;
- experimental sideways/fake-portrait streaming;
- stable signed rolling debug APKs.

Primary architectural principle: extend the working Artemis ownership model rather than creating parallel state systems. Reuse existing profile/layout/persistence owners unless there is a demonstrated reason not to.

---

## 2. Current merged baseline

Latest verified merged `main` at this refresh:

`bc7bf204371290a7c1051773bddbcca3ce39a02f`

Commit:

`Sync durable state after Continuum audit (#19)`

Observed after #19 merge:

- Android CI run #156 — success;
- Build Debug APK run #109 — success on the pre-hardening release workflow.

This SHA is a recovery marker only. Always inspect live `main` before starting work.

### Continuum audit merge sequence after PR #9

The 2026-08-31 / 2026-09-01 audit produced and merged:

1. **#18 — Remove accidental RepoAutoPull artifacts** → `1ceb0b17976f760bb6ad1fd4870a25560ce9713b`
   - removed exactly nine unrelated files accidentally committed directly to `main`;
   - included machine-local config, Windows binaries/shortcuts, and unrelated helper source/docs;
   - no Artemis app/build/signing source was intentionally changed.

2. **#12 — Audit keyboard profile Action bundles** → `9673142c93cf8d3afbe1e8865e774b8ba1b36a58`
   - confirmed modern profile bundles already include layout + custom keys + Artemis Action selections;
   - preserves unknown/future Action IDs inertly and deterministically;
   - rejects unsupported format/version metadata;
   - prevalidates modern bundle structure before writes;
   - keeps legacy single-layout geometry import compatible.

3. **#14 — Editor UI/localization infrastructure polish** → `58ec2d38132b5d30793c23a0165766bb1590891d`
   - standardized major profile/custom-key/Quick Menu/touch-sensitivity/OSC dialog shells on `ArtemisEditorUi`;
   - moved affected shell copy to resources/plurals;
   - added bounded scrolling;
   - did not claim complete localization.

4. **#13 — Deterministic keyboard layout snapping** → `e4187081d4663ad261354c9fdfe9a1e605e4be56`
   - deterministic equal-score candidate tie-break;
   - focused mixed-size/no-resize, center, gap, nearest-candidate and hysteresis tests;
   - intentionally removed an unsafe connected long-label/group-translation implementation before merge.

5. **#15 — Quick Menu / OSC boundary regressions** → `4eec6f0d2178e0dac96521cd4c9961ffcc1e63d3`
   - tests only; persisted Quick Menu depth and OSC name normalization boundaries.

6. **#16 — Sideways-stream layout invariants** → `544c57494661bae5854e38325a67241a67c84666`
   - tests only; CW/CCW sizing, centering, pivots, rotation, off restoration and persistence reset.

7. **#17 — Diana foldable feasibility audit** → `9c01289095c5ef65631d794a8b608febebf50347`
   - documentation/design audit only;
   - no reusable complete cover-screen controller, analog-trigger subsystem, or cover-profile UX was found to port wholesale.

8. **#19 — Post-audit durable state sync** → `bc7bf204371290a7c1051773bddbcca3ce39a02f`
   - synchronized `PROJECT_STATE.md` and `CODEX_HANDOFF.md` after the merged audit set;
   - no product behavior changed.

---

## 3. Major completed product systems

Do not reimplement these from old handoffs.

### Editor / keyboard / Actions

- UI Editor V4 gesture/persistence hardening.
- Named custom key/chord buttons.
- Local Artemis Action buttons with native-style icons and direct-press semantics.
- Managed keyboard profiles: create/select/rename/duplicate/delete/reorder.
- Modern keyboard-profile bundle export/import including layout + custom keys + Action selections.
- Forward-compatible unknown Action-ID preservation.

### OSC / snapping

- Managed gamepad OSC profiles.
- Automatic per-game OSC selection using stable host/app identity.
- Smart snapping/grouping/resizing, group outlines and paired sizing.
- Deterministic best-candidate tie-break from #13.

### Quick Menu

- Versioned persisted nested Quick Menu hierarchy.
- Stable action registry IDs.
- Nested pages, Back navigation, reorder/remove/rename/reset.
- Search/category action picker.
- Runtime-resolved Server Commands / Send Keys / device-specific actions instead of persisting ephemeral entries.
- Parser/editor depth and node limits with malformed-config fallback.

### Orientation / PiP / background lifecycle

- Separate outside-stream orientation policy.
- Settings search over the runtime preference tree.
- Bottom-edge Start gesture with buffered/replayed ordinary touch handling.
- PiP state/overlay restoration hardening and supported-ratio handling.
- Fast Resume retained-session background mode.
- Experimental Keep Connection Alive using a headless decoder Surface where supported, with Fast Resume fallback.
- `Game` no longer uses conflicting `android:noHistory="true"` behavior.
- Experimental Sideways CW / Sideways CCW mode keeping the Activity physically portrait while rotating the logical stream/UI root.

---

## 4. Stable architecture and diagnostic invariants

### Signing identity

Established Artemis Plus certificate SHA-256:

`88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

This public fingerprint is the Android update identity. Never regenerate or replace the private key casually. A missing local `.artemis-signing` directory means restore the private backup, not create a replacement identity.

Never commit keystores, passwords, tokens, or other secrets.

### Android/build safety

- Use the Gradle wrapper.
- Do not casually upgrade Gradle, AGP, SDK/NDK, Java or dependencies during unrelated work.
- Do not weaken/delete regression tests merely to obtain green CI.
- Distinguish inherited/environment-specific Robolectric failures from patch regressions.

### Lifecycle-sensitive areas

When touching streaming/lifecycle code explicitly reason about:

- Activity/Fragment recreation;
- configuration/orientation changes;
- PiP and multi-window;
- background/foreground transitions;
- Surface/TextureView/decoder ownership;
- controller attach/detach and USB reconciliation;
- delayed callbacks and idempotent teardown;
- input suppression/restoration;
- reconnect failure and timeout paths;
- persisted settings/profile recovery.

### Apollo permission trap

A newly paired/reinstalled client can stream video while Apollo has Mouse Input, Keyboard Input, Touch Input, Controller Input or Launch Apps disabled. This can look exactly like an Artemis input regression, including HTTP 403 launch failures. Check Apollo permissions before rewriting client input.

### OEM orientation / MediaCodec reality

Robolectric/emulator success does not prove OEM behavior. OxygenOS orientation, PiP appearance, `MediaCodec.setOutputSurface()`, TextureView restoration, battery/foreground-service behavior and foldable display/posture behavior require physical-device validation when those paths change.

---

## 5. Current audit conclusions and unresolved work

### 5.1 Artemis Action import/export is resolved

Do not restart this because an old handoff calls it missing.

The modern `artemis-plus-keyboard-profiles` format carries per-profile `layout`, `keys` and `actions`. Unknown/future Action IDs are preserved inertly; unsupported modern bundle metadata is rejected; legacy geometry-only import remains compatibility behavior by design.

If a future requirement asks for a different standalone interchange format, first prove a separate user-facing path exists. Never encode Artemis Actions as fake keycodes.

### 5.2 Connected long-label text expansion remains unresolved

An attempted implementation was rejected during #13 audit.

The failed threshold-only approach could move a direct right-edge neighbor but strand an offset descendant connected through that neighbor, breaking topology.

A future fix must use **pre-expansion graph geometry**:

1. identify direct right-edge neighbors of the expanding control;
2. treat the expanding control as a traversal boundary, not a bridge;
3. traverse the old connected graph from those right-edge neighbors;
4. shift every reached descendant by the expansion delta regardless of its absolute X;
5. leave branches attached only on the left/below side unchanged;
6. include a mixed-size/offset descendant regression that fails the old approach.

Do not revive the removed threshold-only translation.

### 5.3 Localization is incomplete

#14 improved shell localization but two important display catalogs remain hard-coded English:

- `ArtemisAction` labels used by picker/content descriptions;
- `StreamActionRegistry` labels/categories/descriptions used by Quick Menu editor/search.

Stable IDs are persistence/runtime contracts. Future localization may resource-back display metadata only; do not translate or mutate IDs.

### 5.4 Sideways streaming remains experimental

Coverage improved in #16, but hardware validation remains for:

- TextureView/decoder Surface attach-destroy timing;
- MediaCodec output-surface switching;
- CW/CCW input transforms;
- PiP blocked/fallback UX;
- IME/system-window behavior;
- Keep Alive transition to/from headless output.

### 5.5 Diana foldable functionality needs a new capability-gated POC

See `DIANA_FOLDABLE_FEASIBILITY.md`. The audited Diana history contains useful compatibility work, not a hidden complete cover-screen controller/analog-trigger feature that can simply be copied.

### 5.6 Release/signing audit — hardening branch in review

Current task branch:

`audit/release-signing-hardening`

Base merged `main`:

`bc7bf204371290a7c1051773bddbcca3ce39a02f`

Audit findings from the pre-hardening release path:

- `Build Debug APK` automatically ran on `audit/**` pushes while carrying `contents: write` and referencing the persistent project signing secrets;
- the workflow computed/printed its signer but did not require the established certificate fingerprint before publishing;
- it did not independently verify every output APK with `apksigner`;
- the old release updater deleted the entire `debug-latest` release before recreating it, creating an avoidable failure window;
- local `setup-signing.ps1` would generate a brand-new key if local signing state was missing and could then overwrite the GitHub secrets with the incompatible identity;
- local `build-apk.ps1` required signing files but did not prove their certificate matched the established project identity.

Hardening implemented on the current audit branch, pending merge/review:

- privileged signed build trigger restricted to `main`; non-main manual dispatch is skipped by the privileged build job;
- top-level workflow token reduced to `contents: read`; only the separate post-verification publish job receives `contents: write`;
- one workflow-level expected fingerprint value is used by signing, APK verification and publication checks so copies cannot drift;
- missing/partial signing secrets fail closed; no audit-key or ephemeral rolling-release fallback;
- reconstructed keystore must match the established certificate before Gradle runs;
- exactly four expected ABI APKs must be present (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`), with exact ABI matching;
- every APK is independently verified with Android `apksigner` against the established certificate;
- checksums/signing metadata are generated only from the verified package;
- existing `debug-latest` release is updated in place instead of being deleted wholesale first;
- new shared `signing-common.ps1` verifies local keystore identity;
- `setup-signing.ps1` is now restore/verify/upload only and refuses automatic replacement-key generation;
- `build-apk.ps1` verifies the local certificate before Gradle starts;
- `SIGNING.md` documents the established identity and failure/recovery model.

**Remaining security limitation:** the four signing values are still repository-level GitHub Actions secrets. Trigger and token hardening materially reduce accidental exposure, but repository secrets are not the strongest isolation boundary for same-repository workflows. The intended stronger end state is migration to a protected main-only GitHub Actions environment (for example `release-signing`) and removal of repository-level copies. Do not switch the workflow to environment-only secret references until those values have actually been migrated from the private signing backup; GitHub does not reveal existing secret values for copying.

After this hardening merges, the main workflow itself must be observed end-to-end before calling the release audit complete: both build and publish jobs successful, rolling tag at merged `main`, exactly four APKs plus `INSTALL.txt`, `SIGNING.txt`, `SHA256SUMS.txt`, and stable signer fingerprint.

---

## 6. Next audit priorities

Remain evidence-driven and keep unrelated fixes in separate coherent PRs.

### Priority A — finish release/signing hardening verification

1. Complete review/CI for `audit/release-signing-hardening`.
2. After merge, observe the new main `Build Debug APK` workflow end-to-end.
3. Verify `debug-latest` tag, release target, asset set, checksums/signing metadata and stable certificate.
4. Treat protected-environment secret migration as separate security hardening requiring access to the backed-up private signing material.

### Priority B — persisted-state robustness audit

Inspect malformed/stale/unknown-ID handling across:

- `QuickMenuConfig`;
- `OscProfilesManager` and per-game mappings;
- `KeyboardProfilesManager`;
- `KeyComboManager`;
- Artemis Action selections;
- floating-control and sideways-mode position storage.

Look specifically for partial writes, stale orphan storage, duplicate IDs/names, unsupported versions, invalid active references, mutable `StringSet` aliasing, apply-order races and destructive recovery from malformed data.

### Priority C — lifecycle/race regression audit

Prioritize safe tests around:

- background park/resume and reconnect failure;
- Keep Alive fallback and delayed teardown ownership;
- Surface/TextureView restoration ordering;
- controller detach while parked;
- PiP/configuration-change interactions;
- delayed UI callbacks after Activity/Fragment teardown.

Prefer narrow regressions over broad rewrites.

### Priority D — repository/security hygiene

Continue checking for generated binaries/build outputs, machine-local paths/configs, secrets/signing material, temporary diagnostics/logs, unrelated helper utilities and release artifacts committed into source.

The RepoAutoPull incident is why this remains explicit.

### Priority E — UI/localization debt

After correctness/lifecycle audits, finish resource-backed display metadata and real-device dialog review without changing stable IDs or introducing a second UI system.

---

## 7. Real-device acceptance checklist for risky areas

When a physical-device pass is available, prioritize:

- normal stream start/stop/reconnect;
- Fast Resume within/after timeout;
- Keep Alive supported and fallback paths;
- background → foreground with controller connected/disconnected;
- PiP enter/exit and overlay restoration;
- Sideways CW/CCW video + touch + custom controls;
- custom-key editor drag/resize/snap with mixed-size controls;
- long custom-key labels near connected controls;
- keyboard profile export/import with custom keys + Actions;
- Quick Menu nested pages/reset;
- OSC per-game mapping/stale repair/profile deletion;
- outside-stream orientation on the target OEM device.

Do not classify an input failure as a client regression until Apollo per-client permissions are checked.

---

## 8. Durable source-of-truth hierarchy

1. **Current source/tests + Git/Actions/Release history** — authoritative.
2. **`PROJECT_STATE.md`** — current roadmap/recovery map; update when conclusions change.
3. **`CODEX_HANDOFF.md`** — rolling latest coherent task/review packet.
4. **`AGENTS.md`** — stable operating and safety rules; remove stale task-specific directions.
5. **`README.md` / `SIGNING.md`** — user-facing and release/signing documentation.
6. **Old uploaded handoffs/chats** — historical intent only; many old TODOs are already implemented.

If these disagree, inspect current source/history and fix stale documentation rather than coding from stale prose.
