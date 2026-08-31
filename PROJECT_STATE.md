# Artemis Plus — Durable Project State and Roadmap

**Last refreshed:** 2026-09-01  
**Purpose:** durable current-state handoff for Codex/ChatGPT and future contributors.  
**Rule:** source code, tests, and current Git history are authoritative. Verify live `main` before acting; this file is a recovery map, not a substitute for inspection.

---

## 1. Project direction

Artemis Plus is an Android streaming client derived from the newer Marssvoodoo Artemis base. The project keeps that newer streaming/reliability base while selectively porting or rethinking useful Diana OSC ideas and adding Artemis-specific quality-of-life features.

The current product direction centers on:

- on-screen controller and custom-key editing;
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

## 2. Current verified repository baseline

Latest merged `main` at this refresh:

`9c01289095c5ef65631d794a8b608febebf50347`

Commit:

`Document Diana foldable feasibility audit (#17)`

This SHA is a recovery marker only. Always inspect the live branch before starting work.

### Continuum audit merge sequence after PR #9

The large 2026-08-31 / 2026-09-01 independent audit produced and merged the following reviewed patches:

1. **#18 — Remove accidental Repo AutoPull artifacts**  
   Merge: `1ceb0b17976f760bb6ad1fd4870a25560ce9713b`
   - removed exactly nine unrelated RepoAutoPull files accidentally committed directly to `main`;
   - removed machine-local config containing an absolute local repository path, generated Windows executables/shortcuts, and the unrelated helper source/readme;
   - no Artemis application/build/signing code changed.

2. **#12 — Audit keyboard profile Action bundles**  
   Merge: `9673142c93cf8d3afbe1e8865e774b8ba1b36a58`
   - confirmed the modern `artemis-plus-keyboard-profiles` bundle already carries profile layout, custom keys, and Artemis Action selections;
   - corrected stale README wording;
   - preserves unknown/future Action IDs inertly across import/export instead of stripping them;
   - Action picker editing preserves opaque IDs;
   - unsupported format/version metadata is rejected;
   - modern bundle structure is prevalidated before profile writes;
   - legacy single-layout geometry import remains compatible.

3. **#14 — Artemis editor UI/localization infrastructure polish**  
   Merge: `58ec2d38132b5d30793c23a0165766bb1590891d`
   - standardized major profile/custom-key/Quick Menu/touch-sensitivity/OSC dialog shells on `ArtemisEditorUi`;
   - moved affected shell copy into Android resources/plurals;
   - added bounded scrolling for compact dialogs;
   - deliberately did **not** claim complete localization.

4. **#13 — Deterministic keyboard layout snapping**  
   Merge: `e4187081d4663ad261354c9fdfe9a1e605e4be56`
   - deterministic tie-break for equal-score snap candidates;
   - focused mixed-size/no-resize, center, gap, nearest-candidate and hysteresis regressions;
   - existing sticky-axis release condition was extracted through a pure helper without changing the threshold rule.
   - **Important audit correction:** an attempted connected-group translation/text-expansion change was found unsafe and removed before merge. `KeyBoardController.java` remained byte-identical to the safe baseline for that behavior.

5. **#15 — Quick Menu / OSC profile boundary regressions**  
   Merge: `4eec6f0d2178e0dac96521cd4c9961ffcc1e63d3`
   - tests only;
   - covers persisted Quick Menu maximum nesting and OSC profile blank-name/80-character normalization.

6. **#16 — Sideways-stream layout invariants**  
   Merge: `544c57494661bae5854e38325a67241a67c84666`
   - tests only;
   - covers sideways CW/CCW measurement, centering, pivots, rotation, off-mode restoration, and clearing both sideways persistence slots.

7. **#17 — Diana foldable feasibility audit**  
   Merge/current head: `9c01289095c5ef65631d794a8b608febebf50347`
   - documentation/design audit only;
   - verified that the inspected Diana history/source contains foldable/configuration compatibility work but no reusable cover-screen controller, virtual analog-trigger subsystem, or full cover-profile UX to port wholesale;
   - records capability-gated POC boundaries and real-device acceptance criteria in `DIANA_FOLDABLE_FEASIBILITY.md`.

### CI note for the audit PRs

The corrected PR heads were green in Android CI before merge. In particular, the narrowed snapping head and the cross-PR-safe localization head were rerun after audit corrections. The GitHub workflow did not immediately expose a separate run for each squash-merge commit when checked, so do not invent post-merge CI claims; inspect current Actions when release/build status matters.

---

## 3. Major completed product systems

Do not reimplement these from an older handoff.

### Editor / keyboard / actions

- UI Editor V4 gesture/persistence hardening.
- Named custom key/chord buttons.
- Local Artemis Action buttons with native-style icons and direct-press semantics.
- Managed keyboard profiles: create/select/rename/duplicate/delete/reorder.
- Modern keyboard profile bundle export/import including layout + custom keys + Action selections.
- Forward-compatible unknown Action-ID preservation.

### OSC / snapping

- Managed gamepad OSC profiles.
- Automatic per-game OSC profile selection using stable host/app identity.
- Smart snapping/grouping/resizing, group outlines and paired sizing.
- Deterministic best-candidate tie-break from audit #13.

### Quick Menu

- Versioned persisted nested Quick Menu hierarchy.
- Stable action registry IDs.
- Nested subpages, Back navigation, reorder/remove/rename/reset.
- Search/category action picker.
- Dynamic runtime Server Commands / Send Keys / device-specific actions rather than persisting ephemeral host/controller entries.
- Parser/editor depth and node-count limits with malformed-config fallback.

### Orientation / PiP / background lifecycle

- Separate outside-stream orientation policy.
- Settings search over the runtime preference tree.
- Bottom-edge Start gesture with buffered/replayed ordinary touch handling.
- PiP state/overlay restoration hardening and supported-ratio handling.
- Fast Resume retained-session background mode.
- Experimental Keep Connection Alive using a headless decoder Surface where supported, with safe Fast Resume fallback.
- `Game` no longer uses conflicting `android:noHistory="true"` behavior.
- Experimental Sideways CW / Sideways CCW mode that keeps the Android Activity physically portrait while transforming the logical stream/UI root.

### Release

- Persistent signing identity and rolling `debug-latest` workflow exist.
- Release/signing state must be audited live before claiming the rolling APK is current.

---

## 4. Important architecture and diagnostic invariants

### Signing identity

Previously verified certificate SHA-256:

`88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

Never regenerate/replace the established signer casually. Never commit keystores, passwords, tokens, or other secrets.

### Android/build safety

- Use the Gradle wrapper.
- Do not casually upgrade Gradle, AGP, SDK/NDK, Kotlin/Java level, or dependencies during unrelated audit work.
- Do not delete/weaken regression tests merely to obtain green CI.
- Distinguish inherited/environment-specific Robolectric failures from regressions introduced by a patch.

### Lifecycle-sensitive areas

When touching streaming/lifecycle code, explicitly reason about:

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

A newly re-paired/reinstalled client can stream video while Apollo has Mouse Input, Keyboard Input, Touch Input, Controller Input, or Launch Apps disabled for that client. This can look exactly like an Artemis input regression (including 403 launch failures). Check Apollo permissions before rewriting client input code.

### OEM orientation / MediaCodec reality

Robolectric/emulator success does not prove OEM behavior. OxygenOS orientation, PiP appearance, `MediaCodec.setOutputSurface()`, TextureView restoration, battery/foreground-service behavior, and foldable posture/display behavior require real-device validation when those paths are changed.

---

## 5. Current audit conclusions and known unresolved work

### 5.1 Artemis Action import/export truth is resolved

Do **not** start another implementation just because an older README/handoff says this is missing.

Current modern flow:

- `KeyboardProfilesManager.exportProfiles()` emits `format`, `version`, and profile objects;
- each profile includes `layout`, `keys`, and `actions`;
- `KeyboardProfilesManager.importProfiles()` restores those per-profile values;
- `ArtemisActionButtonFactory` preserves stable known IDs plus opaque unknown/future IDs;
- malformed/unsupported modern bundle metadata is rejected;
- the legacy single-layout geometry JSON path remains geometry-only compatibility by design.

If future requirements call for a different standalone custom-key interchange format, prove there is a real exposed user flow first; do not fake Artemis Actions as keycodes.

### 5.2 Connected long-label text expansion remains unresolved

The audit intentionally removed an unsafe implementation before #13 merged.

Failure mode found in the rejected approach:

- an expanding control could have a direct right-edge neighbor;
- a descendant connected through that neighbor could begin left of the simple X threshold;
- the direct neighbor moved while the offset descendant was stranded, breaking connected topology.

A future fix must be graph-based using **pre-expansion geometry**:

1. identify direct right-edge neighbors of the expanding control;
2. treat the expanding control as a traversal boundary rather than a bridge;
3. traverse the old connected graph from those right-edge neighbors;
4. shift every reached descendant by the expansion delta regardless of its own absolute X;
5. leave branches attached only on the left/below side unchanged;
6. include a mixed-size/offset descendant regression that would fail the old threshold approach.

Do not revive the removed threshold-only group translation.

### 5.3 Final localization is incomplete

#14 improved shell localization infrastructure but two major catalogs still expose hard-coded English display metadata:

- `ArtemisAction` labels used by Action picker/content descriptions;
- `StreamActionRegistry` labels/categories/descriptions used by Quick Menu editor/search.

Stable IDs are persistence/runtime contracts. A later localization pass may resource-back **display metadata only**; never translate or mutate stable IDs.

### 5.4 Sideways streaming remains experimental

Permanent policy/math/layout coverage is stronger after #16, but real native integration still needs hardware validation for:

- TextureView / decoder Surface attach-destroy timing;
- MediaCodec output-surface switching on OEM devices;
- CW/CCW touch/input transform behavior;
- PiP blocked/fallback UX;
- IME/system-window behavior;
- Keep Alive transition to/from headless output.

### 5.5 Diana foldable features are not a hidden port

See `DIANA_FOLDABLE_FEASIBILITY.md`.

The audited Diana commits include foldable/configuration compatibility work such as layout refreshes and display-pixel handling, but not a complete cover-screen controller/analog-trigger subsystem. Any future implementation should begin as a capability-gated POC, preserve normal non-foldable behavior, and require physical foldable acceptance tests.

---

## 6. Next audit priorities

Remain evidence-driven. These are audit priorities, not permission to mix unrelated feature work into one patch.

### Priority A — release/signing/workflow drift audit

Inspect:

- `.github/workflows/` current triggers and required gates;
- rolling `debug-latest` publication path;
- `SIGNING.md` vs actual workflow behavior;
- current release/tag target vs current `main`;
- signer-verification mechanism and artifact checksums;
- whether squash merges produce/skip expected CI and whether that is intentional.

Read-only first. Do not rotate signing material.

### Priority B — persisted-state robustness audit

Inspect malformed/stale/unknown-ID handling across:

- `QuickMenuConfig`;
- `OscProfilesManager` and per-game mappings;
- `KeyboardProfilesManager`;
- `KeyComboManager`;
- Artemis Action selections.

Look specifically for partial writes, stale orphan storage, duplicate IDs/names, unsupported versions, invalid active references, and non-destructive forward compatibility.

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

Continue checking for:

- generated binaries/build outputs;
- machine-local absolute paths/configs;
- secrets or signing material;
- temp diagnostics/logs;
- accidental helper utilities unrelated to Artemis;
- release artifacts committed into source rather than attached to Releases/Actions.

The RepoAutoPull incident is the reason this remains an explicit audit category.

### Priority E — UI/localization debt

After correctness/lifecycle audits, finish resource-backed display metadata and real-device dialog review without changing stable IDs or introducing a second UI style system.

---

## 7. Real-device acceptance checklist for risky areas

When a physical-device pass is available, prioritize:

- normal stream start/stop/reconnect;
- Fast Resume within/after timeout;
- Keep Alive supported path and fallback path;
- background → foreground with controller connected/disconnected;
- PiP enter/exit and overlay restoration;
- Sideways CW and CCW video + touch + custom controls;
- custom-key editor drag/resize/snap with mixed-size controls;
- long custom-key labels near connected controls;
- keyboard profile export/import with custom keys + Actions;
- Quick Menu nested pages and reset;
- OSC per-game auto mapping, stale mapping repair and profile deletion;
- outside-stream orientation on the target OxygenOS device.

Do not classify an input failure as a client regression until Apollo per-client permissions are checked.

---

## 8. Durable source-of-truth hierarchy

1. **Current source/tests + Git history** — authoritative.
2. **`PROJECT_STATE.md`** — current roadmap/recovery map; update when conclusions change.
3. **`CODEX_HANDOFF.md`** — rolling latest coherent task/review packet.
4. **`README.md`** — user-facing feature/build documentation.
5. **Old uploaded handoffs/chats** — historical intent only; many old TODOs are already implemented.

If these disagree, inspect current source/history and fix the stale document rather than coding from stale prose.
