# SPDX-License-Identifier: AGPL-3.0-only

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Import-Module (Join-Path $PSScriptRoot 'AndroidEmulatorGuard.psm1') -Force

function Assert-Rejected {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string[]]$Lines)
    try {
        Assert-SingleAndroidEmulator -AdbDevicesOutput $Lines | Out-Null
    }
    catch {
        Write-Host "PASS hostile: $Name"
        return
    }
    throw "Guard accepted hostile case: $Name"
}

$valid = Assert-SingleAndroidEmulator -AdbDevicesOutput @(
    'List of devices attached',
    'emulator-5554 device'
)
if ($valid -ne 'emulator-5554') {
    throw "Unexpected emulator serial: $valid"
}
Write-Host 'PASS valid: one ready emulator'

Assert-Rejected -Name 'physical serial' -Lines @('List of devices attached', 'GP520ABC device')
Assert-Rejected -Name 'physical plus emulator' -Lines @(
    'List of devices attached',
    'emulator-5554 device',
    'GP520ABC device'
)
Assert-Rejected -Name 'two emulators' -Lines @(
    'List of devices attached',
    'emulator-5554 device',
    'emulator-5556 device'
)
Assert-Rejected -Name 'offline emulator' -Lines @('List of devices attached', 'emulator-5554 offline')
Assert-Rejected -Name 'multi-word permission state' -Lines @(
    'List of devices attached',
    'emulator-5554 no permissions; see [http://developer.android.com/tools/device.html]'
)
Assert-Rejected -Name 'unparseable adb output' -Lines @('List of devices attached', '* daemon status noise')
Assert-Rejected -Name 'no target' -Lines @('List of devices attached')
