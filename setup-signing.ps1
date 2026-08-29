param(
    [switch]$SkipGitHub,
    [string]$Repo = "juliekeygen-netizen/Artemis-plus"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$SigningDir = Join-Path $PSScriptRoot ".artemis-signing"
$KeystorePath = Join-Path $SigningDir "artemis-plus.jks"
$PropertiesPath = Join-Path $SigningDir "signing.properties"
$Alias = "artemis-plus"

function Find-KeyTool {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    $jdk17 = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like "jdk-17*" |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($jdk17) {
        return (Join-Path $jdk17.FullName "bin\keytool.exe")
    }

    $command = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "JDK keytool was not found. Install JDK 17 with: winget install EclipseAdoptium.Temurin.17.JDK"
}

function New-RandomHexPassword {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return ([BitConverter]::ToString($bytes)).Replace("-", "").ToLowerInvariant()
}

function Read-SigningProperties {
    param([string]$Path)
    $result = @{}
    foreach ($line in Get-Content $Path) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#")) {
            continue
        }
        $parts = $line.Split('=', 2)
        if ($parts.Count -eq 2) {
            $result[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
    return $result
}

function Get-CertificateSha256 {
    param(
        [string]$KeyTool,
        [string]$Store,
        [string]$StorePassword,
        [string]$KeyAlias
    )

    $pemLines = & $KeyTool -exportcert -rfc `
        -keystore $Store `
        -storepass $StorePassword `
        -alias $KeyAlias 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read the signing certificate from $Store"
    }

    $certificateBase64 = ($pemLines |
        Where-Object { $_ -notmatch '^-----' } |
        ForEach-Object { $_.Trim() }) -join ''
    $certificateBytes = [Convert]::FromBase64String($certificateBase64)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha.ComputeHash($certificateBytes)
    } finally {
        $sha.Dispose()
    }
    return (([BitConverter]::ToString($digest)).Replace("-", "").ToLowerInvariant())
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

$keyTool = Find-KeyTool
Write-Host "Keytool : $keyTool" -ForegroundColor DarkGray

$keystoreExists = Test-Path $KeystorePath
$propertiesExist = Test-Path $PropertiesPath
if ($keystoreExists -xor $propertiesExist) {
    throw "Signing setup is incomplete: one of the keystore/properties files is missing. Restore your backup instead of generating a new signing identity."
}

if (-not $keystoreExists) {
    New-Item -ItemType Directory -Path $SigningDir -Force | Out-Null

    # JKS allows separate key/store passwords, but using one strong random local password makes
    # recovery and CI configuration simpler without weakening the private key itself.
    $password = New-RandomHexPassword

    Write-Host "Generating a new Artemis Plus signing identity..." -ForegroundColor Yellow
    & $keyTool -genkeypair `
        -keystore $KeystorePath `
        -storetype JKS `
        -storepass $password `
        -keypass $password `
        -alias $Alias `
        -keyalg RSA `
        -keysize 3072 `
        -validity 10000 `
        -dname "CN=Artemis Plus,O=Artemis Plus,C=FI" `
        -noprompt
    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed to create the Artemis Plus keystore."
    }

    $gradleStorePath = $KeystorePath.Replace('\\', '/')
    @(
        "# Local-only Artemis Plus signing configuration. NEVER COMMIT THIS FILE.",
        "storeFile=$gradleStorePath",
        "storePassword=$password",
        "keyAlias=$Alias",
        "keyPassword=$password"
    ) | Set-Content -Encoding ASCII $PropertiesPath

    Write-Host "Created : $KeystorePath" -ForegroundColor Green
    Write-Host "Created : $PropertiesPath" -ForegroundColor Green
} else {
    Write-Host "Existing signing identity found; reusing it (no key regeneration)." -ForegroundColor Green
}

$props = Read-SigningProperties $PropertiesPath
foreach ($required in @("storePassword", "keyAlias", "keyPassword")) {
    if (-not $props.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($props[$required])) {
        throw "Missing '$required' in $PropertiesPath"
    }
}

$password = $props["storePassword"]
$Alias = $props["keyAlias"]
$keyPassword = $props["keyPassword"]
$fingerprint = Get-CertificateSha256 -KeyTool $keyTool -Store $KeystorePath -StorePassword $password -KeyAlias $Alias

$infoPath = Join-Path $SigningDir "BACKUP-THIS-KEY.txt"
@(
    "ARTEMIS PLUS SIGNING KEY - BACK THIS DIRECTORY UP",
    "",
    "Certificate SHA-256: $fingerprint",
    "Keystore: $KeystorePath",
    "",
    "Back up the entire .artemis-signing directory somewhere private.",
    "If this key is lost, future builds cannot update installations signed by it.",
    "Never commit or publicly upload the keystore/signing.properties files."
) | Set-Content -Encoding UTF8 $infoPath

Write-Host "Signer SHA-256: $fingerprint" -ForegroundColor Cyan
Write-Host ""
Write-Host "IMPORTANT: Back up the entire .artemis-signing folder somewhere private." -ForegroundColor Yellow
Write-Host "Losing this key means future builds cannot update installs signed by it." -ForegroundColor Yellow
Write-Host ""

if (-not $SkipGitHub) {
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $gh) {
        throw "The local signing key is ready, but GitHub CLI is not installed. Install it with 'winget install --id GitHub.cli', reopen PowerShell, run 'gh auth login', then run this script again."
    }

    & gh auth status 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "The local signing key is ready, but GitHub CLI is not authenticated. Run 'gh auth login', then run this script again."
    }

    Write-Host "Uploading the signing material to encrypted GitHub Actions secrets..." -ForegroundColor Yellow
    $keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeystorePath))
    Set-GitHubSecretFromStdin -Name "ARTEMIS_PLUS_KEYSTORE_BASE64" -Value $keystoreBase64 -Repository $Repo
    Set-GitHubSecretFromStdin -Name "ARTEMIS_PLUS_KEYSTORE_PASSWORD" -Value $password -Repository $Repo
    Set-GitHubSecretFromStdin -Name "ARTEMIS_PLUS_KEY_ALIAS" -Value $Alias -Repository $Repo
    Set-GitHubSecretFromStdin -Name "ARTEMIS_PLUS_KEY_PASSWORD" -Value $keyPassword -Repository $Repo
    Write-Host "GitHub Actions secrets configured for $Repo." -ForegroundColor Green
}

Write-Host ""
Write-Host "SIGNING SETUP COMPLETE" -ForegroundColor Green
Write-Host "Future local and GitHub builds can now use the same Android signing identity." -ForegroundColor Green
Write-Host ""
