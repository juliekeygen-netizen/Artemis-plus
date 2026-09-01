# Artemis Plus — Codex Review Handoff

This is the rolling review packet for the latest coherent agent task. Verify live GitHub state before relying on recorded SHAs because the final handoff commit itself advances the branch.

---

## Current handoff

### Task

Modernize the GitHub Actions runtime stack to current Node-24-capable action majors without changing the already-proven Android SDK/NDK, signing identity, ABI set, or rolling-release semantics.

### User goal

Continue auditing and fixing autonomously for as long as useful work remains. Do not stop after one patch; after this maintenance item, continue through resource/build warnings, persistence/lifecycle regressions, UI debt, and useful feature work where safe.

### Repository state

- Base branch: `main`
- Base commit for this clean replacement branch: `4228653a51c64bec18bfced869603a910db37ed1` (`Recover OSC profiles from damaged metadata (#25)`)
- Task branch: `maintenance/node24-github-actions-v2`
- Workflow implementation commit before this handoff update: `daaf0534ab09faa3107d0b4a2aa6e3c2982705e9`
- Superseded stale PR: #26 from `maintenance/node24-github-actions`; it diverged after #25 merged and must remain closed/unmerged.

### Newly completed OSC/release verification

PR #25 is merged and fully proven beyond PR CI:

- merge commit: `4228653a51c64bec18bfced869603a910db37ed1`;
- final PR head passed compile, focused Artemis regressions, full inherited unit suite, and report upload;
- main Build Debug APK run #115 (`33493172057`) passed both `build` and `publish` jobs;
- `debug-latest` points exactly to `4228653a51c64bec18bfced869603a910db37ed1`;
- release target matches that commit;
- rolling release contains exactly seven expected assets: four ABI APKs plus `INSTALL.txt`, `SIGNING.txt`, and `SHA256SUMS.txt`;
- signer identity remains the established Artemis Plus SHA-256:
  `88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`.

The OSC recovery path now preserves valid siblings during partial metadata corruption and can reconstruct referenced/snapshotted missing profiles without treating harmless duplicate metadata as evidence to resurrect unrelated stale mappings.

### Workflow modernization in this branch

The branch changes only `.github/workflows/android-ci.yml` and `.github/workflows/build-debug-apk.yml` before this handoff file:

- `actions/checkout@v4 -> @v7`;
- `actions/setup-java@v4 -> @v6`;
- `android-actions/setup-android@v3 -> @v4`;
- `actions/upload-artifact@v4 -> @v7`;
- `actions/download-artifact@v4 -> @v8`;
- explicitly pins `cmdline-tools-version: 12266719` so setup-android v4 does not silently move the proven Android command-line tools from 16.0 to 20.0;
- adds `maintenance/**` to Android CI push branches so maintenance branches prove themselves before PR merge.

### Scope guard

Do not accidentally broaden this maintenance change. It intentionally does **not** change:

- the established signer fingerprint;
- signing secret names or privilege separation;
- `contents: read` build-job / `contents: write` publish-job model;
- main-only privileged signed-build policy;
- Android platform 36 install;
- NDK `27.0.12077973`;
- Java 17;
- four configured ABI APKs;
- Gradle build targets;
- release version generation;
- `debug-latest` rolling-tag/update-in-place behavior;
- signer parser/helper semantics.

### Validation already obtained

The exact workflow blobs being carried onto this v2 branch were already exercised on the superseded maintenance branch in Android CI push run #196 and passed:

- checkout v7;
- setup-java v6;
- setup-android v4 with pinned command-line tools;
- SDK/NDK install;
- non-root debug compilation;
- focused Artemis regression suite;
- full inherited unit suite;
- upload-artifact v7.

Because `build-debug-apk.yml` is intentionally privileged/main-only, its `upload-artifact@v7` + `download-artifact@v8` + publish path still requires a real post-merge main run before the modernization can be called end-to-end proven.

### Required merge/release sequence

1. Verify this replacement branch differs from current `main` only in the two workflows plus this handoff.
2. Open a replacement PR and close superseded #26.
3. Require fresh Android CI on the v2 branch/PR.
4. Merge only if the exact current head is green and mergeable.
5. Inspect the resulting main Build Debug APK run:
   - build success;
   - signer gate success;
   - all four APK signer verifications success;
   - artifact upload v7 success;
   - artifact download v8 success;
   - publish success.
6. Verify `debug-latest` tag/release target and seven-asset package again.

### Other confirmed audit findings still open

- Russian, Simplified Chinese, and Traditional Chinese `title_render_mode_balance_shift_description` resources contain literal `%` characters. They are used only as static Preference summaries, so `formatted="false"` is the appropriate narrow warning fix; avoid risky whole-file replacement without a safe patch path.
- Perf-chart localized resources are stripped during packaging because several localized-only entries appear to lack default resources. Determine whether these are dead feature strings or missing base resources before adding defaults.
- Debug has `minifyEnabled true` while remaining debuggable. AGP disables optimization/obfuscation in that combination; do not change it only to silence a warning. Compare intended shrink/package behavior first.
- Gradle reports deprecated features that will be incompatible with Gradle 9. Audit separately rather than mixing build-system upgrades into this workflow-runtime PR.
- Connected long-label control expansion remains intentionally unresolved. Do not revive the previously rejected threshold-only group translation; any future fix must use pre-expansion graph topology.
- Full Artemis Action / Quick Menu display localization remains incomplete; stable persisted action IDs must never be translated.

### Next audit queue after this PR

1. Finish the static `%` resource warning with a minimal safe resource edit and build validation.
2. Trace perf-chart resource definitions and call sites; fix only if actual missing-default behavior is demonstrated.
3. Audit debug minification/shrinking semantics and Gradle deprecations.
4. Continue persisted-state robustness across keyboard profiles, key combos, Quick Menu, OSC, floating positions, and sideways state.
5. Continue lifecycle/race coverage around background park/resume, Keep Alive fallback, surfaces, controller detach, PiP/configuration changes, and delayed callbacks.
6. If correctness/audit work is exhausted, move into useful contained product features rather than stopping.
