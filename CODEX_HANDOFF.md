# Artemis Plus — Codex Review Handoff

This is the rolling review packet for the latest coherent agent task. Verify live GitHub state before relying on recorded SHAs because the final handoff commit itself advances the branch.

---

## Current handoff

### Task

Harden persisted OSC-profile metadata recovery after the broader Artemis Plus Continuum audit found that malformed profile-list metadata could orphan otherwise valid per-profile controller snapshots.

### User goal

Continue auditing and fixing autonomously. Preserve user configuration across corruption, upgrades, restarts, profile switching, and future-version compatibility; add regression coverage; do not stop after one patch while useful work remains.

### Repository state

- Base branch: `main`
- Base commit for this branch: `423eb27b009c25d53d5a7bc19bd789af756afb17` (`Preserve future Quick Menu action IDs (#23)`)
- Task branch: `fix/osc-profile-metadata-recovery-v2`
- Latest implementation commit before this handoff update: `0f0cfda36dae8f3880c9125380e597d3f00da64e`
- Pull request: #25 `Recover OSC profiles from damaged metadata`

### Completed release/signing audit

The release/signing sub-audit is now end-to-end proven and no longer blocked:

- #20 hardened persistent signing, main-only privileged release, read/write job separation, ABI checks, local fail-closed signer validation, and regression CI.
- #21/#22 fixed and fixture-tested the live `apksigner --print-certs` digest parser.
- #23 preserved unknown future Quick Menu action IDs while keeping runtime/action creation registry-gated.
- Main Build Debug APK #114 on `423eb27b...` passed both build and publish jobs.
- All four ABI APKs passed `apksigner verify` and matched the established Artemis Plus certificate SHA-256:
  `88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`
- `debug-latest` points exactly to `423eb27b...`.
- The rolling release contains exactly seven expected assets: four ABI APKs plus `INSTALL.txt`, `SIGNING.txt`, and `SHA256SUMS.txt`; no stale extras remained.

Do not weaken or duplicate those signer/release gates in later work.

### OSC corruption bug

OSC profile metadata is stored in `ArtemisPlusOscProfiles`, while each profile snapshot is stored independently under an `OSC_PROFILE_<id>` preference file and identified by `snapshot_initialized_<id>` metadata. Active-profile and per-game mappings also retain profile IDs.

Before this branch, malformed `profiles` JSON could collapse to a new Default profile and leave valid snapshot data orphaned. Wrong-typed SharedPreferences values could also trigger class-cast failures on reads.

### Current recovery behavior

`OscProfilesManager` now:

1. treats a missing profile-list key as normal first-run state and does **not** promote unrelated stale mappings;
2. treats malformed, blank, or wrong-typed existing profile-list metadata as corruption;
3. reconstructs recoverable profile IDs deterministically from:
   - active-profile metadata,
   - per-game mappings,
   - `snapshot_initialized_<id> == true` markers;
4. always retains/repairs the Default profile;
5. filters unusable recovered IDs (blank or over 200 characters);
6. preserves valid siblings if only one stored profile entry is malformed;
7. after the second audit pass, also merges recoverable IDs when corruption is **partial** rather than only when every parsed profile is lost;
8. distinguishes simple duplicate metadata from true entry loss so a duplicate alone does not resurrect an unrelated stale mapping;
9. handles wrong-type active/game/snapshot values without `ClassCastException` and repairs stale metadata on read;
10. copies `StringSet` snapshot values into fresh sets rather than sharing mutable preference-backed sets.

Recovered IDs that no longer have a stored name receive deterministic labels such as `Recovered OSC Profile 1`.

### Regression coverage

`OscProfilesManagerTest` covers:

- missing Default repair and persistence;
- malformed top-level profile-list recovery;
- deterministic recovery ordering from active/game/snapshot references;
- malformed single-entry isolation while keeping valid siblings;
- partial corruption recovering a referenced/snapshotted missing profile while retaining valid siblings;
- duplicate metadata not resurrecting an unrelated stale mapping;
- wrong-type metadata repair;
- invalid active-profile fallback;
- create/rename/activate/delete lifecycle;
- profile-name normalization/length bound;
- Default deletion refusal;
- per-game set/change/clear/delete behavior;
- stale per-game mapping repair;
- host/app-scoped game-profile keys and delimiter escaping.

### Validation

- Initial clean current-main recovery branch head `41546cf...`: Android CI #194 passed compile, focused Artemis regressions, full inherited unit suite, and report upload.
- The partial-corruption refinement advanced the head to `0f0cfda...`; a fresh CI run is required before merge.
- PR #25 is mergeable, but must remain unmerged until that exact head is green and its final base-to-head diff is rechecked.

### Parallel maintenance work

A separate branch `maintenance/node24-github-actions` is modernizing GitHub Action runtimes without changing the proven Android SDK/NDK or release semantics:

- checkout `v4 -> v7`
- setup-java `v4 -> v6`
- setup-android `v3 -> v4`
- upload-artifact `v4 -> v7`
- download-artifact `v4 -> v8`
- `cmdline-tools-version: 12266719` is explicitly pinned so setup-android v4 does not silently move the toolchain from command-line tools 16.0 to 20.0.

That branch must independently pass Android CI, then the post-merge main signed release must be checked because artifact download/publish actions changed.

### Other audit findings still open

- Static localized `title_render_mode_balance_shift_description` strings in Russian, Simplified Chinese, and Traditional Chinese contain literal `%` signs and trigger Android resource-format warnings. The string is used as a static Preference summary; `formatted="false"` is semantically appropriate, but large locale-file replacement has not yet been risked through the connector.
- Perf-chart localization resources are removed during packaging because several localized-only strings lack required default values. Audit whether they are dead features or missing base resources before fixing.
- Debug build reports both debuggable and minify enabled; Gradle disables optimizations/obfuscation for debuggable builds. Audit whether this is intentional legacy configuration.
- Gradle reports deprecated features that will be incompatible with Gradle 9. Audit separately rather than mixing into persistence fixes.
- Continue lifecycle/race/persistence and long-label connected-control audits after these contained patches.

### Reviewer / next-agent action

1. Verify PR #25 at its live head; require the rerun for `0f0cfda...` to be green.
2. Re-audit the exact two-file OSC diff, especially first-run vs corruption boundaries and stale-reference resurrection.
3. Merge #25 only if the exact head remains clean.
4. Continue the Node-24 workflow maintenance branch and its live main release validation.
5. Keep moving through remaining persistence, resource, lifecycle, and UI regression findings rather than stopping after one merge.
