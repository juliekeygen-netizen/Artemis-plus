param(
    [switch]$SkipGitHub,
    [string]$Repo = "juliekeygen-netizen/Artemis-plus"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
. (Join-Path $PSScriptRoot "signing-common.ps1")

$SigningDir = Join-Path $PSScriptRoot ".artemis-signing"
$KeystorePath = Join-Path $SigningDir "artemis-plus.jks"
$PropertiesPath = Join-Path $SigningDir "signing.properties"

function Test-GitHubCliAuth {
    param([string]$GhPath)

    # gh can write status information to stderr. Keep it out of PowerShell's error stream and
    # decide success exclusively from the native exit code.
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $GhPath
    $startInfo.Arguments = "auth status"
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        return $false
    }
    $null = $process.StandardOutput.ReadToEnd()
    $null = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return ($process.ExitCode -eq 0)
}

function Set-GitHubSecretFromStdin {
    param(
        [string]$Name,
        [string]$Value,
        [string]$Repository
    )

    # Write the secret directly to gh's stdin without PowerShell's usual trailing newline. A hidden
    # newline in a keystore password is enough to make every CI signing attempt fail.
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = "gh"
    $startInfo.Arguments = "secret set $Name --repo $Repository"
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Unable to start GitHub CLI while setting secret '$Name'."
    }
    $process.StandardInput.Write($Value)
    $process.StandardInput.Close()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "Failed to set GitHub secret '$Name': $stderr$stdout"
    }
}

Write-Host ""
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host " Artemis Plus - Persistent Signing Setup" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host ""

$expectedFingerprint = Get-ArtemisPlusExpectedCertificateSha256
$keystoreExists = Test-Path $KeystorePath
$propertiesExist = Test-Path $PropertiesPath

if ($keystoreExists -xor $propertiesExist) {
    throw "Signing setup is incomplete: one of the keystore/properties files is missing. Restore the backed-up .artemis-signing directory; do not generate a replacement identity."
}

if (-not $keystoreExists) {
    throw @"
The established Artemis Plus signing identity is not present in this clone.
Expected certificate SHA-256: $expectedFingerprint

Refusing to generate a new key. A new key would make future APKs unable to update existing Artemis Plus installations and could overwrite the valid GitHub signing secrets.

Restore the backed-up .artemis-signing directory, then run this script again.
"@
}

$keyTool = Find-ArtemisPlusKeyTool
Write-Host "Keytool : $keyTool" -ForegroundColor DarkGray
Write-Host "Keystore: $KeystorePath" -ForegroundColor DarkGray

$props = Read-ArtemisPlusSigningProperties $PropertiesPath
foreach ($required in @("storePassword", "keyAlias", "keyPassword")) {
    if (-not $props.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($props[$required])) {
        throw "Missing '$required' in $PropertiesPath"
    }
}

$password = $props["storePassword"]
$alias = $props["keyAlias"]
$keyPassword = $props["keyPassword"]
$fingerprint = Assert-ArtemisPlusSigningIdentity `
    -KeyTool $keyTool `
    -Store $KeystorePath `
    -StorePassword $password `
    -KeyAlias $alias

# A restored backup may contain an absolute storeFile path from another PC/folder. After the
# certificate is verified, repair only that location field while preserving the real credentials.
$gradleStorePath = $KeystorePath -replace '\\', '/'
@(
    "# Local-only Artemis Plus signing configuration. NEVER COMMIT THIS FILE.",
    "storeFile=$gradleStorePath",
    "storePassword=$password",
    "keyAlias=$alias",
    "keyPassword=$keyPassword"
) | Set-Content -Encoding ASCII $PropertiesPath

$infoPath = Join-Path $SigningDir "BACKUP-THIS-KEY.txt"
@(
    "ARTEMIS PLUS SIGNING KEY - BACK THIS DIRECTORY UP",
    "",
    "Certificate SHA-256: $fingerprint",
    "Expected certificate SHA-256: $expectedFingerprint",
    "Keystore: $KeystorePath",
    "",
    "Back up the entire .artemis-signing directory somewhere private.",
    "If this key is lost, future builds cannot update installations signed by it.",
    "Never commit or publicly upload the keystore/signing.properties files."
) | Set-Content -Encoding UTF8 $infoPath

Write-Host "Verified signer SHA-256: $fingerprint" -ForegroundColor Green
Write-Host ""
Write-Host "IMPORTANT: Back up the entire .artemis-signing folder somewhere private." -ForegroundColor Yellow
Write-Host "This script will not generate a replacement if that backup is lost." -ForegroundColor Yellow
Write-Host ""

if (-not $SkipGitHub) {
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $gh) {
        throw "The local signing key is verified, but GitHub CLI is not installed. Install it with 'winget install --id GitHub.cli', reopen PowerShell, run 'gh auth login', then run this script again."
    }

    if (-not (Test-GitHubCliAuth -GhPath $gh.Source)) {
        throw "The local signing key is verified, but GitHub CLI is not authenticated. Run 'gh auth login', then run this script again."
    }

    # Upload only after the restored/local key has been proven to be the established identity.
    Write-Host "Uploading the verified signing material to encrypted GitHub Actions secrets..." -ForegroundColor Yellow
    $keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeystorePath))
    Set-GitHubSecretFromStdin -Name "ARTEMIS_PLUS_KEYSTORE_BASE64" -Value $keystoreBase64 -Repository $Repo
    Set-GitHubSecretFromStdin -Name "ARTEMIS_PLUS_KEYSTORE_PASSWORD" -Value $password -Repository $Repo
    Set-GitHubSecretFromStdin -Name "ARTEMIS_PLUS_KEY_ALIAS" -Value $alias -Repository $Repo
    Set-GitHubSecretFromStdin -Name "ARTEMIS_PLUS_KEY_PASSWORD" -Value $keyPassword -Repository $Repo
    Write-Host "GitHub Actions secrets configured for $Repo using the verified signer." -ForegroundColor Green
}

Write-Host ""
Write-Host "SIGNING SETUP VERIFIED" -ForegroundColor Green
Write-Host "Local and GitHub builds can use the established Artemis Plus signing identity." -ForegroundColor Green
Write-Host ""
