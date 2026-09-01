# Artemis Plus — Codex Review Handoff

This is the rolling review packet for the latest coherent Codex/agent task. Verify live GitHub state before relying on any recorded SHA because the final handoff commit itself necessarily advances the branch.

---

## Current handoff

### Task

Release/signing/workflow security and reliability audit.

### User goal

Continue the Continuum audit autonomously, fix important defects rather than merely report them, keep the established Android update signer safe, and continue into further useful audits instead of stopping for routine guidance.

### Repository state

- Base branch: `main`
- Base commit: `bc7bf204371290a7c1051773bddbcca3ce39a02f` (`Sync durable state after Continuum audit (#19)`)
- Task branch: `audit/release-signing-hardening`
- Last implementation commit before this final handoff update: `789df14aec25cd92b04b5652b15b28367ee1155a`
- Pull request: #20 — `Harden release signing identity and workflow`

### Scope completed

#### GitHub Actions release path

`.github/workflows/build-debug-apk.yml` was audited against the actual current Actions/release behavior.

The old workflow:

- ran automatically on `main` **and `audit/**`** pushes;
- granted `contents: write` to the whole workflow;
- referenced the persistent project signing repository secrets on audit-branch runs;
- computed the signer fingerprint but did not require the established fingerprint before publication;
- did not independently verify the certificate on every output APK;
- deleted the entire rolling `debug-latest` release before recreating it.

The hardened branch now:

- automatically runs the signed release workflow only on `main`;
- skips the privileged build job for a non-main manual dispatch;
- uses `contents: read` by default and grants `contents: write` only to the separate publish job after verification;
- requires all persistent signing values and has no ephemeral/throwaway rolling-release signer fallback;
- uses one workflow-level public expected certificate fingerprint so signing/build/publish checks cannot drift independently;
- rejects the reconstructed keystore unless it matches the established Artemis Plus certificate;
- requires exactly one APK for each configured ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) and exactly four APKs total;
- uses exact ABI filename matching so `x86` cannot accidentally count `x86_64`;
- independently verifies every APK certificate with Android `apksigner`;
- creates checksums/install/signing metadata only after package verification;
- moves the rolling tag only after the package is verified;
- updates an existing `debug-latest` release in place with `gh release upload --clobber` + `gh release edit` rather than deleting the whole release first;
- creates the release only when it does not already exist.

#### CI validation infrastructure

`.github/workflows/android-ci.yml` now parses `signing-common.ps1`, `setup-signing.ps1`, and `build-apk.ps1` with PowerShell's parser before Android compilation/tests. This is a syntax-only gate; it does not execute the signing helpers or expose secrets.

#### Local signing tooling

New `signing-common.ps1` centralizes local certificate inspection and stable-fingerprint enforcement.

`setup-signing.ps1` previously generated a new JKS when `.artemis-signing` was absent, then could upload that incompatible identity over the GitHub repository secrets. Because Artemis Plus now has an established update identity, that behavior was unsafe.

It now:

- requires an existing/restored keystore + properties pair;
- rejects partial/missing local signing state;
- refuses automatic replacement-key generation;
- verifies the actual certificate against the established fingerprint before changing anything;
- repairs only the local absolute `storeFile` path after a valid backup is restored to another clone/PC;
- uploads values to GitHub only after identity verification.

`build-apk.ps1` now verifies the actual local certificate before Gradle starts, refuses machine-specific debug-keystore fallback, and explicitly binds Gradle's `ARTEMIS_PLUS_KEYSTORE_*` environment inputs to the exact keystore/password/alias tuple that was just verified. A stale or edited `storeFile` in `signing.properties` therefore cannot make the helper verify one keystore and silently build with another.

`SIGNING.md` now documents the project as being in the long-term preserve-the-established-identity phase rather than initial key creation.

### Stable signing invariant

Expected certificate SHA-256:

`88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

This is public certificate identity metadata. The private keystore/passwords remain secrets.

### Important residual security limitation

The four signing values are still repository-level GitHub Actions secrets:

- `ARTEMIS_PLUS_KEYSTORE_BASE64`
- `ARTEMIS_PLUS_KEYSTORE_PASSWORD`
- `ARTEMIS_PLUS_KEY_ALIAS`
- `ARTEMIS_PLUS_KEY_PASSWORD`

Trigger and token hardening reduce accidental exposure, but repository-level secrets are not the strongest boundary against same-repository workflow edits.

The stronger end state is a protected GitHub Actions environment (for example `release-signing`) restricted to `main`, followed by deletion of the repository-level copies. Do **not** switch the workflow to environment-only references until the real values have been migrated from the backed-up private signing material; GitHub does not reveal existing secret values for copying.

This connector session cannot recover those secret values, so the branch intentionally preserves the functioning repository-secret references while documenting the safer migration path.

### Files materially changed

- `.github/workflows/android-ci.yml` — PowerShell syntax gate
- `.github/workflows/build-debug-apk.yml`
- `signing-common.ps1` — new
- `setup-signing.ps1`
- `build-apk.ps1`
- `SIGNING.md`
- `README.md` — rolling-release/signing guidance
- `AGENTS.md` — signing safety + stale roadmap correction
- `PROJECT_STATE.md` — durable release audit state/next priorities
- `CODEX_HANDOFF.md` — this review packet

No Android application source, product persistence format, native streaming code, Gradle configuration, or private signing material is intentionally changed.

### Compatibility / safety behavior

- Existing stable signer is preserved; no key material was rotated.
- Existing repository secret names remain unchanged so the current release setup can continue working.
- Rolling tag remains `debug-latest`.
- Build target remains `:app:assembleNonRoot_gameDebug`.
- Expected release APK set remains the four configured ABI splits already produced by `app/build.gradle`.
- Android update compatibility is now explicitly fail-closed on signer mismatch.
- Local users with a valid backed-up `.artemis-signing` directory can restore it on a new path; only `storeFile` is normalized after certificate verification.
- Local builds force Gradle to the exact verified keystore rather than trusting a persisted `storeFile` path.

### Audit defects found during implementation itself

Independent self-review caught and fixed three would-be regressions/gaps before merge:

1. the first ABI validator used a broad `*x86*`-style glob that would also count `x86_64`; it was replaced with exact `*-<abi>-debug.apk` suffix matching;
2. one manually duplicated expected-fingerprint literal briefly contained an extra `008`; the workflow now has a single top-level expected-fingerprint value used by all three stages, removing that drift class;
3. local `build-apk.ps1` initially verified the fixed `.artemis-signing/artemis-plus.jks` path while Gradle could still honor a different `storeFile` from `signing.properties`; the helper now exports all four `ARTEMIS_PLUS_KEYSTORE_*` inputs from the verified key/properties before Gradle starts.

### Tests / CI actually observed

- Pre-hardening merged baseline `bc7bf204...`:
  - Android CI #156 — PASS
  - old Build Debug APK #109 — PASS
- Current audit branch:
  - Android CI #161 on intermediate head `5f9187ed...` — PASS
  - Android CI #170 on implementation head `0191bb94d947ff42294f601b5ae0bf4a69ad756c` — PASS
  - Android CI #174 on final implementation head `789df14aec25cd92b04b5652b15b28367ee1155a` — PASS
    - `Validate Artemis Plus PowerShell helper syntax` — PASS
    - non-root debug Java compile — PASS
    - focused Artemis Plus regression suite — PASS
    - full inherited unit suite step — PASS

The privileged Build Debug APK workflow intentionally does **not** execute on this audit branch anymore. Therefore its new signing/APK/publish path cannot be honestly declared proven until the patch merges to `main` and the new main workflow is observed end-to-end.

This final Markdown-only handoff update may trigger one additional Android CI run; require it to remain green before merge.

### Post-merge verification required

After this PR is audited and merged:

1. verify main Android CI passes;
2. verify the new `Build Debug APK` run has both `build` and `publish` jobs successful;
3. inspect job steps/logs for signer/ABI/APK-verification success;
4. verify `debug-latest` points to the merged main commit;
5. verify release assets are exactly four ABI APKs plus `INSTALL.txt`, `SIGNING.txt`, `SHA256SUMS.txt`;
6. verify release/signing metadata reports the stable fingerprint above;
7. if the workflow fails, fix it immediately on a follow-up audit branch rather than weakening signer checks.

### Audit hotspots

Review especially:

- GitHub Actions YAML job-level `if`, permissions and outputs;
- bash exact-ABI matching and SDK/apksigner discovery;
- `keytool -exportcert` fingerprint calculation vs `apksigner` fingerprint format;
- rolling tag force-update followed by release asset/metadata update;
- PowerShell native-process handling in `signing-common.ps1`;
- restored `signing.properties` path repair without credential mutation;
- local Gradle environment binding to the already-verified keystore;
- absence of any key-generation path in `setup-signing.ps1`;
- absence of secrets/private key material in the diff.

### Deferred work

- protected GitHub Actions environment + migration/removal of repository-level signing secrets;
- persisted-state robustness audit;
- lifecycle/race audit;
- remaining localization display metadata;
- unresolved graph-based long-label connected-control expansion.

### PROJECT_STATE update

`PROJECT_STATE.md` records `bc7bf204...` as the latest merged baseline, describes this release hardening as pending review rather than merged, records the remaining environment-secret limitation, and makes post-merge release verification the immediate audit priority before persisted-state work.

### Suggested reviewer action

Audit this branch and merge only after the final branch Android CI is green and the diff contains no unrelated/private material. After merge, observe the new main release workflow end-to-end before declaring the signing/release audit complete.

---

## Required shape for the next coherent handoff

Replace the current section with the next task's real branch/base/head/PR, exact scope, files, compatibility/state ownership, lifecycle/persistence considerations, tests actually run, Actions/release state, limitations, audit hotspots, deferred work, and suggested reviewer action. Do not claim external or device validation that was not observed.
