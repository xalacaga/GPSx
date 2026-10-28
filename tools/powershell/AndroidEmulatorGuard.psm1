# SPDX-License-Identifier: AGPL-3.0-only

Set-StrictMode -Version Latest

function Get-AndroidDeviceEntries {
    param([Parameter(Mandatory)][AllowEmptyString()][string[]]$Lines)

    foreach ($line in $Lines) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed -eq 'List of devices attached') {
            continue
        }
        if ($trimmed -notmatch '^(?<serial>\S+)\s+(?<state>.+)$') {
            throw "Unrecognized adb devices line: '$trimmed'."
        }
        [pscustomobject]@{
            Serial = $Matches['serial']
            State = $Matches['state'].Trim()
        }
    }
}

function Assert-SingleAndroidEmulator {
    param([Parameter(Mandatory)][AllowEmptyString()][string[]]$AdbDevicesOutput)

    $entries = @(Get-AndroidDeviceEntries -Lines $AdbDevicesOutput)
    if ($entries.Count -ne 1) {
        throw "Instrumentation requires exactly one ADB target; found $($entries.Count)."
    }
    $entry = $entries[0]
    if ($entry.Serial -notmatch '^emulator-\d+$') {
        throw "Refusing instrumentation on non-emulator ADB serial '$($entry.Serial)'."
    }
    if ($entry.State -ne 'device') {
        throw "Emulator '$($entry.Serial)' is not ready (state=$($entry.State))."
    }
    return $entry.Serial
}

Export-ModuleMember -Function Get-AndroidDeviceEntries, Assert-SingleAndroidEmulator
