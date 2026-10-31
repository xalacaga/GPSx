# SPDX-License-Identifier: AGPL-3.0-only
# Builds the signed release APK from the clean, pushed HEAD and publishes a GitHub
# release with gpsx.apk and release.json (read by the in-app updater and Downloader).
# Signing material comes from ~/.gpsx/signing.properties and never enters the repository.
[CmdletBinding()]
param(
    [string]$Repository = 'xalacaga/GPSx',
    [switch]$Prerelease
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$app = Join-Path $root 'apps\android-tv'
$dist = Join-Path $root '.tools\dist'

if (git -C $root status --porcelain --untracked-files=all) { throw 'The worktree must be clean.' }
$revision = (git -C $root rev-parse HEAD).Trim()
$branch = (git -C $root rev-parse --abbrev-ref HEAD).Trim()
git -C $root fetch --quiet origin $branch
if ((git -C $root rev-parse "origin/$branch").Trim() -ne $revision) { throw "Push $branch before publishing." }

$gradleFile = Get-Content -Raw (Join-Path $app 'app\build.gradle.kts')
$versionCode = [int]([regex]::Match($gradleFile, 'versionCode = (\d+)').Groups[1].Value)
$versionName = [regex]::Match($gradleFile, 'versionName = "([^"]+)"').Groups[1].Value
$tag = "v$versionName"

Push-Location $app
try {
    & .\gradlew.bat --console=plain :app:assembleRelease "-PgpsxSourceRevision=$revision"
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
} finally {
    Pop-Location
}

$apk = Join-Path $app 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw 'Signed release APK not found; check ~/.gpsx/signing.properties.' }
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Copy-Item $apk (Join-Path $dist 'gpsx.apk') -Force
$sha = (Get-FileHash -Algorithm SHA256 (Join-Path $dist 'gpsx.apk')).Hash.ToLowerInvariant()
$json = [ordered]@{
    versionCode = $versionCode
    versionName = $versionName
    apkSha256 = $sha
    packageName = 'com.gpsx.tv'
    sourceRevision = $revision
} | ConvertTo-Json
# UTF-8 without byte-order mark, also on Windows PowerShell 5.1.
[System.IO.File]::WriteAllText((Join-Path $dist 'release.json'), $json, (New-Object System.Text.UTF8Encoding $false))

$notes = @"
GPSx $versionName for Android TV (BenQ GP520 test build).

Install with Downloader: https://github.com/$Repository/releases/latest/download/gpsx.apk
In-app updates use release.json (SHA-256 $sha).

Corresponding source: https://github.com/$Repository/tree/$revision
License: AGPL-3.0-only. The native engine is Pylux ca87a49 (AGPL-3.0-only with OpenSSL exception)
plus third_party/pylux/gpsx-pylux-ca87a49.patch; rebuild with tools/powershell/build-pylux-native.ps1.
"@
$flags = @()
if ($Prerelease) { $flags += '--prerelease' }
gh release create $tag (Join-Path $dist 'gpsx.apk') (Join-Path $dist 'release.json') `
    --repo $Repository --target $revision --title "GPSx $versionName" --notes $notes @flags
if ($LASTEXITCODE -ne 0) { throw 'gh release create failed.' }
Write-Host "Published $tag : https://github.com/$Repository/releases/tag/$tag"
