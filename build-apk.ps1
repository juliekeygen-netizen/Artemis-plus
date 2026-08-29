param(
    [switch]$OpenFolder
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$OutputName = "Artemis-Plus-debug-arm64.apk"
$OutputPath = Join-Path $PSScriptRoot $OutputName

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Artemis Plus - Local ARM64 APK Builder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path ".\gradlew.bat")) {
    throw "gradlew.bat was not found. Run this script from the Artemis Plus repository."
}

# Prefer the same Java major version used by GitHub Actions. If JAVA_HOME is
# missing or points at a JRE, automatically use an installed Temurin JDK 17.
$javac = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\javac.exe"
    if (Test-Path $candidate) {
        $javac = $candidate
    }
}

if (-not $javac) {
    $jdk17 = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like "jdk-17*" |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if (-not $jdk17) {
        throw "JDK 17 was not found. Install it with: winget install EclipseAdoptium.Temurin.17.JDK"
    }

    $env:JAVA_HOME = $jdk17.FullName
    $env:Path = "$($jdk17.FullName)\bin;$env:Path"
    $javac = Join-Path $jdk17.FullName "bin\javac.exe"
}

Write-Host "Java : $env:JAVA_HOME" -ForegroundColor DarkGray

# Make sure Gradle can find the Android SDK. local.properties is intentionally
# local-only and should not be committed.
if (-not (Test-Path ".\local.properties")) {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) {
        $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }

    if (-not (Test-Path $sdk)) {
        throw "Android SDK was not found. Expected it at '$sdk'."
    }

    $sdkForGradle = $sdk -replace '\\', '/'
    "sdk.dir=$sdkForGradle" | Set-Content -Encoding ASCII ".\local.properties"
    Write-Host "SDK  : $sdk (created local.properties)" -ForegroundColor DarkGray
} else {
    Write-Host "SDK  : using local.properties" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "Building non-root debug APKs..." -ForegroundColor Yellow
Write-Host ""

& .\gradlew.bat :app:assembleNonRoot_gameDebug
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE."
}

$apk = Get-ChildItem ".\app\build\outputs\apk" -Recurse -File -Filter "*.apk" |
    Where-Object { $_.Name -match "arm64-v8a" -and $_.Name -match "debug" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $apk) {
    throw "Build completed, but no ARM64 debug APK was found under app\build\outputs\apk."
}

Copy-Item $apk.FullName $OutputPath -Force
$copied = Get-Item $OutputPath
$sizeMb = [Math]::Round($copied.Length / 1MB, 1)

Write-Host ""
Write-Host "BUILD + COPY SUCCESSFUL" -ForegroundColor Green
Write-Host "Source : $($apk.FullName)" -ForegroundColor DarkGray
Write-Host "APK    : $OutputPath" -ForegroundColor Green
Write-Host "Size   : $sizeMb MB" -ForegroundColor DarkGray
Write-Host ""

if ($OpenFolder) {
    explorer.exe "/select,`"$OutputPath`""
}
