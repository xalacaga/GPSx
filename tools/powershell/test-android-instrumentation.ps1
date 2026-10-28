# SPDX-License-Identifier: AGPL-3.0-only

[CmdletBinding()]
param(
    [string]$AvdName = 'GPSx_GoogleTV_API36',
    [switch]$Asan
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$androidRoot = Join-Path $repositoryRoot 'apps\android-tv'
$sdkRoot = Join-Path $repositoryRoot '.tools\android-sdk'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$gradle = Join-Path $androidRoot 'gradlew.bat'
$resultRoot = Join-Path $androidRoot 'app\build\outputs\androidTest-results\connected'
$evidenceRoot = Join-Path $androidRoot 'app\build\reports\gpsx-instrumentation-evidence'
$startedEmulator = $false
$emulatorProcess = $null
$serial = $null

Import-Module (Join-Path $PSScriptRoot 'AndroidEmulatorGuard.psm1') -Force

foreach ($required in @($adb, $emulator, $gradle)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing Android instrumentation prerequisite: $required"
    }
}

function Read-AdbDevices {
    $lines = @(& $adb devices)
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed ($LASTEXITCODE)."
    }
    return $lines
}

try {
    $initialEntries = @(Get-AndroidDeviceEntries -Lines (Read-AdbDevices))
    if ($initialEntries.Count -eq 0) {
        $emulatorProcess = Start-Process -FilePath $emulator -ArgumentList @(
            '-avd', $AvdName,
            '-no-window',
            '-no-audio',
            '-no-boot-anim',
            '-no-snapshot-save'
        ) -WindowStyle Hidden -PassThru
        $startedEmulator = $true

    }

    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($null -ne $emulatorProcess -and $emulatorProcess.HasExited) {
            throw "Android emulator exited with code $($emulatorProcess.ExitCode)."
        }
        $entries = @(Get-AndroidDeviceEntries -Lines (Read-AdbDevices))
        if ($entries.Count -gt 1 -or ($entries.Count -eq 1 -and $entries[0].Serial -notmatch '^emulator-\d+$')) {
            Assert-SingleAndroidEmulator -AdbDevicesOutput (Read-AdbDevices) | Out-Null
        }
        if ($entries.Count -eq 1 -and $entries[0].State -eq 'device') {
            $serial = $entries[0].Serial
            break
        }
        Start-Sleep -Seconds 1
    }
    if ($null -eq $serial) {
        throw 'A single ready emulator did not appear in ADB within 60 seconds.'
    }

    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $bootCompleted = (& $adb -s $serial shell getprop sys.boot_completed).Trim()
        if ($LASTEXITCODE -eq 0 -and $bootCompleted -eq '1') {
            break
        }
        Start-Sleep -Seconds 1
    }
    if ($bootCompleted -ne '1') {
        throw 'Android emulator did not finish booting within 90 seconds.'
    }

    # Revalidate immediately before Gradle can install anything. Any newly
    # attached physical or second virtual target makes the gate fail closed.
    $serial = Assert-SingleAndroidEmulator -AdbDevicesOutput (Read-AdbDevices)
    $priorSerial = $env:ANDROID_SERIAL
    try {
        $env:ANDROID_SERIAL = $serial
        $gradleArguments = @('-p', $androidRoot, ':app:connectedDebugAndroidTest', '--no-daemon')
        if ($Asan) {
            $gradleArguments += '-PgpsxAsan=true'
            $gradleArguments += '-Pandroid.testInstrumentationRunnerArguments.class=com.gpsx.tv.nativeadapter.NativeAdapterLifecycleTest'
        }
        if (Test-Path -LiteralPath $resultRoot) {
            Remove-Item -LiteralPath $resultRoot -Recurse -Force
        }
        & $gradle @gradleArguments
        if ($LASTEXITCODE -ne 0) {
            throw "Android instrumentation failed ($LASTEXITCODE)."
        }

        $resultFiles = @(Get-ChildItem -LiteralPath $resultRoot -Recurse -Filter 'TEST-*.xml')
        $testCount = 0
        foreach ($resultFile in $resultFiles) {
            [xml]$result = Get-Content -LiteralPath $resultFile.FullName -Raw
            $testCount += [int]$result.testsuites.tests
        }
        if ($resultFiles.Count -eq 0 -or $testCount -eq 0) {
            throw 'Android instrumentation produced no executed tests; refusing a false-success gate.'
        }
        $evidenceName = if ($Asan) { 'asan' } else { 'normal' }
        $evidenceDestination = Join-Path $evidenceRoot $evidenceName
        if (Test-Path -LiteralPath $evidenceDestination) {
            Remove-Item -LiteralPath $evidenceDestination -Recurse -Force
        }
        New-Item -ItemType Directory -Path $evidenceDestination -Force | Out-Null
        foreach ($resultFile in $resultFiles) {
            Copy-Item -LiteralPath $resultFile.FullName -Destination $evidenceDestination
        }
        Write-Host "Android instrumentation executed $testCount tests on $serial."
    }
    finally {
        $env:ANDROID_SERIAL = $priorSerial
    }
}
finally {
    if ($startedEmulator -and $null -ne $serial) {
        & $adb -s $serial emu kill 2>$null | Out-Null
    }
    elseif ($startedEmulator -and $null -ne $emulatorProcess -and -not $emulatorProcess.HasExited) {
        Stop-Process -Id $emulatorProcess.Id -Force
    }
}
