# EA-FB: local Windows build. Does not run GitHub Actions or upload any files.
$ErrorActionPreference = "Stop"
Set-Location (Resolve-Path (Join-Path $PSScriptRoot ".."))
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw "Install JDK 21 and add java to PATH." }
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) { throw "Install Gradle 8.12 and add gradle to PATH." }
if (-not (Get-Command python -ErrorAction SilentlyContinue)) { throw "Install Python 3 and add python to PATH." }
if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    throw "Install Android SDK API 35 and set ANDROID_HOME or ANDROID_SDK_ROOT."
}
$javaVersion = (& java -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "21\.') { throw "JDK 21 required: $javaVersion" }
$gradleVersion = (& gradle --version | Out-String)
if ($gradleVersion -notmatch 'Gradle 8\.12(?:\s|$)') { throw "This build expects Gradle 8.12." }
& gradle ":EA-FB:make" "makePluginsJson" "--no-daemon"
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed. No release staged." }
& python "scripts/stage-release.py"
if ($LASTEXITCODE -ne 0) { throw "Release checks failed. Do not publish." }
Write-Host "Local build staged in dist/. Test on Android before uploading."
