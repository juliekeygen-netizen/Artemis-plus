# Shared Artemis Plus signing helpers.
#
# The certificate fingerprint is public identity metadata, not a secret. Keeping the established
# value in source lets local and CI tooling fail closed if a different private key is selected.

function Get-ArtemisPlusExpectedCertificateSha256 {
    return "88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083"
}

function Find-ArtemisPlusKeyTool {
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

function Read-ArtemisPlusSigningProperties {
    param([Parameter(Mandatory = $true)][string]$Path)

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

function Get-ArtemisPlusCertificateSha256 {
    param(
        [Parameter(Mandatory = $true)][string]$KeyTool,
        [Parameter(Mandatory = $true)][string]$Store,
        [Parameter(Mandatory = $true)][string]$StorePassword,
        [Parameter(Mandatory = $true)][string]$KeyAlias
    )

    # Windows PowerShell 5 may turn harmless native stderr into NativeCommandError when
    # ErrorActionPreference is Stop. Use ProcessStartInfo and trust the real exit code instead.
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $KeyTool
    $startInfo.Arguments = "-exportcert -rfc -keystore `"$Store`" -storepass `"$StorePassword`" -alias `"$KeyAlias`""
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Unable to start keytool while reading the Artemis Plus signing certificate."
    }

    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "Unable to read the signing certificate from $Store. keytool exit code $($process.ExitCode): $stderr"
    }

    $certificateBase64 = (($stdout -split "`r?`n") |
        Where-Object { $_ -and $_ -notmatch '^-----' } |
        ForEach-Object { $_.Trim() }) -join ''
    if ([string]::IsNullOrWhiteSpace($certificateBase64)) {
        throw "keytool returned no signing certificate data for alias '$KeyAlias'."
    }

    $certificateBytes = [Convert]::FromBase64String($certificateBase64)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha.ComputeHash($certificateBytes)
    } finally {
        $sha.Dispose()
    }

    return (([BitConverter]::ToString($digest)).Replace("-", "").ToLowerInvariant())
}

function Assert-ArtemisPlusSigningIdentity {
    param(
        [Parameter(Mandatory = $true)][string]$KeyTool,
        [Parameter(Mandatory = $true)][string]$Store,
        [Parameter(Mandatory = $true)][string]$StorePassword,
        [Parameter(Mandatory = $true)][string]$KeyAlias
    )

    $expected = Get-ArtemisPlusExpectedCertificateSha256
    $actual = Get-ArtemisPlusCertificateSha256 `
        -KeyTool $KeyTool `
        -Store $Store `
        -StorePassword $StorePassword `
        -KeyAlias $KeyAlias

    if (-not $actual.Equals($expected, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw @"
Artemis Plus signing identity mismatch.
Expected certificate SHA-256: $expected
Actual certificate SHA-256:   $actual
Refusing to continue because APKs signed by this key cannot update established Artemis Plus installs.
Restore the backed-up .artemis-signing directory instead of creating or uploading a replacement key.
"@
    }

    return $actual.ToLowerInvariant()
}
