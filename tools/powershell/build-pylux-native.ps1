# SPDX-License-Identifier: AGPL-3.0-only
# Rebuilds the GPSx native Remote Play engine: Pylux at the pinned commit plus
# third_party/pylux/gpsx-pylux-ca87a49.patch, RelWithDebInfo, stripped, all ABIs.
# Output: .tools/pylux-ca87a49-release/lib/<abi>/libchiaki-jni.so (consumed by apps/android-tv).
[CmdletBinding()]
param(
    [string]$ProtobufPython = (Join-Path $PSScriptRoot '..\..\.tools\protobuf-venv\Scripts\python.exe')
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$commit = 'ca87a49a12bab071948f565ca53ffdad790775d3'
$source = Join-Path $root '.tools\pylux-ca87a49'
$patch = Join-Path $root 'third_party\pylux\gpsx-pylux-ca87a49.patch'
$output = Join-Path $root '.tools\pylux-ca87a49-release\lib'
$sdk = Join-Path $root '.tools\android-sdk'
$strip = Join-Path $sdk 'ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe'

if (-not (Test-Path $source)) {
    git clone --filter=blob:none https://github.com/ForWard-Technologies-LLC/Pylux.git $source
}
git -C $source fetch --quiet origin $commit
git -C $source checkout --quiet --force $commit
git -C $source reset --quiet --hard $commit
git -C $source submodule update --init --depth 1 third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl android/app/src/main/cpp/oboe
git -C $source apply --whitespace=nowarn $patch
if ($LASTEXITCODE -ne 0) { throw 'The GPSx Pylux patch did not apply.' }

Set-Content -LiteralPath (Join-Path $source 'android\local.properties') -Value ("sdk.dir=" + ($sdk -replace '\\', '/')) -Encoding ascii
$env:GPSX_PROTOBUF_PYTHON = (Resolve-Path $ProtobufPython).Path
$env:PATH = (Split-Path $env:GPSX_PROTOBUF_PYTHON) + ';' + $env:PATH
Push-Location (Join-Path $source 'android')
try {
    & .\gradlew.bat --no-daemon :app:externalNativeBuildRelease
    if ($LASTEXITCODE -ne 0) { throw 'Pylux native release build failed.' }
} finally {
    Pop-Location
}

$built = Get-ChildItem -Path (Join-Path $source 'android\app\build\intermediates\cxx\RelWithDebInfo') -Recurse -Filter 'libchiaki-jni.so'
foreach ($abi in 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64') {
    $library = $built | Where-Object { $_.Directory.Name -eq $abi } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $library) { throw "Missing libchiaki-jni.so for $abi" }
    $target = Join-Path $output $abi
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    & $strip --strip-unneeded -o (Join-Path $target 'libchiaki-jni.so') $library.FullName
    if ($LASTEXITCODE -ne 0) { throw "Strip failed for $abi" }
}
Write-Host "Native engine ready in $output"
