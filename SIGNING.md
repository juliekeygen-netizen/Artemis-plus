# Artemis Plus persistent APK signing

Artemis Plus rolling debug builds use one established project signing identity so a newer APK can be installed directly over an older Artemis Plus APK without uninstalling the app or losing its data.

## Established identity — do not rotate casually

The durable Artemis Plus certificate SHA-256 fingerprint is:

`88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083`

This fingerprint is public identity metadata, not a secret. The private keystore and passwords are secret.

Android update compatibility depends on keeping this exact signer. A different key is not a harmless replacement: devices with an established Artemis Plus install will reject an APK signed by another key as an update.

## Restore/setup on Windows

The project identity already exists. `setup-signing.ps1` is therefore a **restore/verify/upload** helper, not a key generator.

From the repository root, after restoring the backed-up `.artemis-signing` directory:

```powershell
.\setup-signing.ps1
```

The script now:

1. Requires both `.artemis-signing/artemis-plus.jks` and `.artemis-signing/signing.properties` to exist.
2. Reads the real certificate from the keystore and requires it to match the established fingerprint above.
3. Repairs only the local `storeFile` path after a backup was moved to a different PC/folder.
4. Rewrites `.artemis-signing/BACKUP-THIS-KEY.txt` with the verified identity.
5. Only after successful verification, uploads the keystore/password values to the repository's encrypted GitHub Actions secrets using GitHub CLI (`gh`).

If the signing directory is absent, the script **refuses to generate a new key**. Restore the private backup instead. This prevents a fresh clone or lost local folder from silently replacing the GitHub signer and breaking update compatibility.

The `.artemis-signing/` directory is ignored by Git and must never be committed.

If GitHub CLI is not installed, install it with:

```powershell
winget install --id GitHub.cli
```

Then authenticate once:

```powershell
gh auth login
```

## Back up the private key

Back up the entire `.artemis-signing` directory somewhere private and keep more than one safe copy. Losing the established private key means later APKs cannot update devices that already have Artemis Plus installed with this identity.

Do not upload the keystore or `signing.properties` publicly.

## Local builds

After restoring/verifying the signing identity:

```powershell
.\build-apk.ps1
```

The local build helper:

- refuses to use Android's machine-specific debug keystore;
- requires the local Artemis Plus signing files;
- verifies the actual keystore certificate against the established fingerprint before Gradle starts;
- builds the non-root debug variant and copies the ARM64 APK to `Artemis-Plus-debug-arm64.apk`.

Shared fingerprint/keytool validation lives in `signing-common.ps1` so setup and local build behavior cannot drift independently.

## GitHub Actions rolling release

`.github/workflows/build-debug-apk.yml` is a privileged main-only release workflow.

Important invariants:

- automatic execution is limited to pushes to `main`;
- a manual dispatch from any non-`main` ref is skipped by the privileged build job;
- the build job has `contents: read` only;
- persistent signing secrets are required — no ephemeral signer fallback is permitted for the rolling release;
- the reconstructed keystore certificate must match the established fingerprint before Gradle runs;
- the four expected non-root debug ABI APKs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) must all exist and no unexpected fifth APK may enter the package;
- every APK is independently checked with Android `apksigner` and must report the established certificate fingerprint;
- only the separate publish job receives `contents: write`, after the build/signature checks have succeeded;
- the stable `debug-latest` prerelease is recreated at the verified `main` commit and carries APK checksums plus install/signing metadata.

Feature/audit branches use the normal Android CI workflow rather than the privileged signing/release workflow.

The encrypted signing values currently used by CI are:

- `ARTEMIS_PLUS_KEYSTORE_BASE64`
- `ARTEMIS_PLUS_KEYSTORE_PASSWORD`
- `ARTEMIS_PLUS_KEY_ALIAS`
- `ARTEMIS_PLUS_KEY_PASSWORD`

### Remaining secret-scope hardening

Those values are currently repository-level GitHub Actions secrets. GitHub repository secrets are available to workflows in the repository, so trigger hardening alone is defense in depth rather than a complete secret-isolation boundary.

The stronger end state is to move the four signing values to a dedicated GitHub Actions **environment** (for example `release-signing`) whose deployment branch policy allows only `main`, then reference that environment from the signing job and remove the repository-level copies. Environment secrets are only exposed to jobs that reference the environment and satisfy its protection rules.

Do not switch the workflow to environment-only secrets until the existing secret values have actually been migrated; otherwise the rolling build would fail. Secret values cannot be recovered from GitHub after creation, so migration must use the backed-up local signing material rather than trying to read the current repository secrets.

## Release verification

Every rolling release contains:

- four ABI-specific debug APKs;
- `SHA256SUMS.txt` for the APK files;
- `INSTALL.txt` with build/install guidance;
- `SIGNING.txt` containing the commit, version, expected certificate fingerprint, actual verified certificate fingerprint, and APK signer-verification result.

The public release notes also record the signer SHA-256. If it ever differs from the established fingerprint above, treat the build as invalid and do not install it.

## Versioning

Local and CI test builds use UTC epoch minutes as their Android `versionCode`, which increases over time and stays consistent between local and cloud builds. Their version name includes the Artemis Plus build number and short Git commit SHA.

## Historical first migration

Builds published before persistent signing used disposable CI debug keys. Android cannot update from one signer to a different signer.

The one-time migration to the current established signer has already happened. That is why the tooling now refuses automatic key regeneration: the project is in the long-term **preserve this identity** phase, not initial key creation.
