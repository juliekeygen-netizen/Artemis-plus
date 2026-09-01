# Artemis Plus — Codex Review Handoff

This is the rolling review packet for the latest coherent agent task. Verify live GitHub state before relying on recorded SHAs because the final handoff commit itself advances the branch.

---

## Current handoff

### Task

Repair the live-only APK signer-fingerprint parser failure discovered immediately after release/signing hardening PR #20 merged.

### User goal

Continue the Continuum audit autonomously, fix important defects rather than merely report them, preserve the established Android update signer, verify release behavior on real GitHub Actions, and continue into persistence/lifecycle work after the release path is genuinely proven.

### Repository state

- Base branch: `main`
- Base commit: `9de0df8cfeb1206bc9e209406176f35a7b954ac3` (`Harden release signing identity and workflow (#20)`)
- Task branch: `fix/release-apksigner-fingerprint-parser`
- Last implementation commit before this handoff update: `d35e7ab20e5f6a731e4509f7d5d026f6a667e917`
- Pull request: pending at time of this handoff update

### Why this follow-up exists

PR #20 intentionally moved the privileged signed-release workflow to `main` only. Its audit branch could validate ordinary Android CI but could not exercise repository signing secrets or publication. After #20 merged, the first hardened main release run exposed one live-only parser defect.

Observed main runs on merge commit `9de0df8c...`:

- Android CI #177 (`33484848328`) — **PASS**
- Build Debug APK #111 (`33484848383`) — **FAIL**, safely before publication

Build Debug APK #111 proved that the core hardened path worked up to APK-certificate text parsing:

- persistent signing secrets were present;
- reconstructed JKS verification passed;
- verified JKS certificate SHA-256 was exactly the established Artemis Plus fingerprint;
- `:app:assembleNonRoot_gameDebug` passed;
- all four expected ABI APKs were produced with exact filenames;
- package preparation then failed with `Unable to read signer fingerprint from dist/app-nonRoot_game-arm64-v8a-debug.apk`;
- artifact upload did not run;
- write-scoped publish job was skipped;
- the existing `debug-latest` release remained intact with its previous seven assets.

### Root cause

The workflow correctly used Android `apksigner verify --print-certs`, but then parsed its human-readable certificate line with an overly strict delimiter:

```text
awk -F': ' '/Signer #1 certificate SHA-256 digest:/ ...
```

The runner's installed build-tools produced a successfully verified APK but the expected exact `colon + space` output shape was not captured, leaving an empty parsed fingerprint. This was a parser compatibility failure, not a signing-identity mismatch.

### Fix

`.github/workflows/build-debug-apk.yml` now keeps `apksigner verify --print-certs` as the cryptographic gate but parses certificate metadata defensively:

- capture the first case-insensitive line containing `certificate`, `SHA-256`, and `digest`;
- split at the first colon regardless of following whitespace;
- remove whitespace and optional colon separators from the digest;
- normalize hex to lowercase;
- require exactly 64 hexadecimal characters before comparison;
- compare the normalized digest to the established Artemis Plus fingerprint;
- print the selected `apksigner` path/version and each verified APK fingerprint for future diagnosis;
- on malformed/unrecognized output, print diagnostic certificate output and fail closed rather than publish.

No signer check was removed or weakened.

### Stable signing invariant

Expected certificate SHA-256:

`88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

No private key/password material is included or rotated.

### Validation performed so far

- Main Android CI #177 on the #20 merge commit: PASS.
- Main signed build #111:
  - JKS stable-fingerprint verification: PASS;
  - Gradle four-ABI APK build: PASS;
  - APK output count/names: PASS;
  - old strict certificate-text parsing: FAIL;
  - publication: SKIPPED / fail closed.
- Existing `debug-latest` release checked after failure: still present with four ABI APKs plus `INSTALL.txt`, `SIGNING.txt`, `SHA256SUMS.txt`.
- New parser was exercised locally against:
  - canonical `digest: <64hex>`;
  - `digest:<64hex>` with no space;
  - colon-separated uppercase hex;
  - extra whitespace;
  - malformed non-hex input.
  All valid variants normalized to the established fingerprint; malformed input was rejected.
- Current GitHub CLI release-edit/create flags were audited separately; `--target`, `--verify-tag`, `--title`, `--notes-file`, and `--prerelease` are valid current flags, so no additional known publish-command defect was found before retrying the main path.

### Files intentionally changed

- `.github/workflows/build-debug-apk.yml`
- `CODEX_HANDOFF.md`

No Android product source, Gradle signing configuration, keystore, secret value, APK, or release asset is intentionally changed in this follow-up.

### Required PR / post-merge verification

Before merge:

1. open a PR targeting `main` so normal Android PR CI runs;
2. inspect exact base-to-head diff for unrelated/private material;
3. require Android CI green;
4. merge only at the audited expected head.

After merge, do not declare the release audit complete until a new main `Build Debug APK` run proves all of these:

1. JKS fingerprint check passes;
2. build produces exactly four expected ABI APKs;
3. all four APKs pass `apksigner verify`;
4. all four parsed certificate digests equal the established fingerprint;
5. verified release artifact uploads successfully;
6. write-scoped `publish` job succeeds;
7. `debug-latest` points at the new merged main commit;
8. release contains exactly four APKs plus `INSTALL.txt`, `SIGNING.txt`, `SHA256SUMS.txt`;
9. release/signing metadata reports the established fingerprint.

If another live-only failure appears, fix the pipeline rather than weakening signer or package checks.

### Remaining hardening / next audits

- protected main-only GitHub Actions environment and migration/removal of repository-level signing secrets (requires real secret values from the private backup; connector cannot retrieve them);
- confirmed Quick Menu forward-compatibility defect: older builds currently delete persisted unknown future action IDs even though the editor can display them as unavailable and runtime safely ignores them; a separate fix branch/tests were prepared but should be rebased/recreated from latest main after this release repair is durable;
- deeper OSC profile malformed-metadata recovery audit;
- broader persisted-state robustness audit;
- lifecycle/race audit;
- remaining localization display metadata;
- unresolved graph-based long-label connected-control expansion.

### Suggested reviewer action

Audit this small release-parser follow-up, merge only when PR CI is green, then inspect the resulting privileged main release run step-by-step. The release/signing audit remains open until that real run publishes a verified seven-asset rolling release successfully.
