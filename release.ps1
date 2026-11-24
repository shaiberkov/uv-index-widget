# Publish a new version of UV Index to everyone who installed it.
#
#   .\release.ps1 -Version 1.8 -Notes "מה חדש בגרסה"
#
# Bumps the version, builds a signed APK, commits, tags, pushes and creates a
# GitHub Release. Installed apps find it within ~6 hours (or right away when opened).
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notes = "עדכון גרסה"
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$gradleFile = "app/build.gradle.kts"
$gradle = Get-Content $gradleFile -Raw -Encoding UTF8
if ($gradle -notmatch 'versionCode = (\d+)') { throw "versionCode not found" }
$code = [int]$Matches[1] + 1
$gradle = $gradle -replace 'versionCode = \d+', "versionCode = $code"
$gradle = $gradle -replace 'versionName = "[^"]+"', "versionName = `"$Version`""
[IO.File]::WriteAllText((Resolve-Path $gradleFile), $gradle, (New-Object Text.UTF8Encoding $false))

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }
.\gradlew.bat assembleRelease
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$apk = "app/build/outputs/apk/release/UvIndex-$Version.apk"
Copy-Item "app/build/outputs/apk/release/app-release.apk" $apk -Force

git add -A
git commit -m "Release v$Version"
git tag "v$Version"
git push origin HEAD --tags
gh release create "v$Version" $apk --title "UV Index $Version" --notes $Notes
Write-Host "Released v$Version (versionCode $code)" -ForegroundColor Green
