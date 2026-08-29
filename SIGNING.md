# Artemis Plus persistent APK signing

Artemis Plus rolling debug builds use one persistent project signing identity so a newer APK can be installed directly over an older Artemis Plus APK without uninstalling the app or losing its data.

## One-time setup on Windows

From the repository root:

```powershell
.\setup-signing.ps1
```

The script:

1. Generates `.artemis-signing/artemis-plus.jks` once using a 3072-bit RSA key.
2. Generates a strong random password and stores the local Gradle signing configuration in `.artemis-signing/signing.properties`.
3. Prints the signing certificate SHA-256 fingerprint and writes it to `.artemis-signing/BACKUP-THIS-KEY.txt`.
4. Uploads the keystore and passwords to the repository as encrypted GitHub Actions secrets using GitHub CLI (`gh`).

The `.artemis-signing/` directory is ignored by Git and must never be committed.

If GitHub CLI is not installed, install it with:

```powershell
winget install --id GitHub.cli
```

Then authenticate once:

```powershell
gh auth login
```

Re-running `setup-signing.ps1` reuses the existing key. It does **not** regenerate a new identity.

## Back up the private key

Back up the entire `.artemis-signing` directory somewhere private. Losing the key means later APKs cannot update devices that already have an Artemis Plus build signed by that key.

Do not upload the keystore or `signing.properties` publicly.

## Local builds

After the one-time setup:

```powershell
.\build-apk.ps1
```

The local build helper refuses to build if persistent signing is missing, preventing accidental APKs signed with a machine-specific Android debug key.

## GitHub Actions

The rolling `debug-latest` workflow reconstructs the keystore only inside the temporary GitHub Actions runner from these encrypted repository secrets:

- `ARTEMIS_PLUS_KEYSTORE_BASE64`
- `ARTEMIS_PLUS_KEYSTORE_PASSWORD`
- `ARTEMIS_PLUS_KEY_ALIAS`
- `ARTEMIS_PLUS_KEY_PASSWORD`

A `main` build refuses to publish if any persistent-signing secret is missing. Audit branches may use a disposable audit key for build validation, but they never publish a Release.

Every published Release includes `SIGNING.txt` with the certificate SHA-256 fingerprint so the signing identity can be checked between builds.

## Versioning

Local and CI test builds use UTC epoch minutes as their Android `versionCode`, which increases over time and stays consistent between local and cloud builds. Their version name includes the Artemis Plus build number and short Git commit SHA.

## First migration

Builds published before persistent signing used disposable CI debug keys. Android cannot update from one signer to a different signer.

Therefore, when persistent signing is enabled for the first time, the currently installed old debug build must be uninstalled **one final time** before installing the first persistent-signed build. After that migration, later Artemis Plus APKs signed with the same project key can update normally without uninstalling.
