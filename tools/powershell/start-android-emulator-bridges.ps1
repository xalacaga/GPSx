[CmdletBinding()]
param(
    [string]$Serial = '',
    [string]$Avd = 'GPSx_GoogleTV_API36',
    [ValidateRange(30, 600)]
    [int]$BootTimeoutSeconds = 180,
    [switch]$HideGamepadBridge
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$androidSdk = Join-Path $repositoryRoot '.tools\android-sdk'
$adb = Join-Path $androidSdk 'platform-tools\adb.exe'
$emulator = Join-Path $androidSdk 'emulator\emulator.exe'
$scrcpy = Join-Path $repositoryRoot '.tools\scrcpy-win64-v5.0\scrcpy.exe'
$relay = Join-Path $repositoryRoot 'target\debug\gpsx-discover.exe'

foreach ($required in @($adb, $emulator, $scrcpy)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required tool is missing: $required"
    }
}

function Get-ReadyEmulatorSerials {
    $lines = & $adb devices 2>$null
    @(
        foreach ($line in $lines) {
            if ($line -match '^(emulator-[0-9]+)\s+device\s*$') {
                $Matches[1]
            }
        }
    )
}

$readySerials = @(Get-ReadyEmulatorSerials)
if ($Serial) {
    if ($Serial -notmatch '^emulator-[0-9]+$') {
        throw "Only an Android emulator serial is allowed: $Serial"
    }
    if ($readySerials -notcontains $Serial) {
        throw "Requested emulator is not ready: $Serial"
    }
} elseif ($readySerials.Count -eq 1) {
    $Serial = $readySerials[0]
} elseif ($readySerials.Count -gt 1) {
    throw 'Several emulators are ready. Run again with -Serial emulator-NNNN.'
} else {
    $availableAvds = @(& $emulator -list-avds)
    if ($availableAvds -notcontains $Avd) {
        throw "Android Virtual Device is missing: $Avd"
    }

    Write-Host "Starting Android Virtual Device: $Avd"
    $emulatorProcess = Start-Process `
        -FilePath $emulator `
        -ArgumentList @('-avd', $Avd) `
        -WorkingDirectory (Split-Path -Parent $emulator) `
        -PassThru

    $deadline = [DateTime]::UtcNow.AddSeconds($BootTimeoutSeconds)
    do {
        if ($emulatorProcess.HasExited) {
            throw "Android emulator exited before becoming ready. Exit code: $($emulatorProcess.ExitCode)"
        }
        Start-Sleep -Seconds 1
        $readySerials = @(Get-ReadyEmulatorSerials)
    } until ($readySerials.Count -gt 0 -or [DateTime]::UtcNow -ge $deadline)

    if ($readySerials.Count -ne 1) {
        throw "Android emulator did not become ready within $BootTimeoutSeconds seconds."
    }
    $Serial = $readySerials[0]
}

$bootDeadline = [DateTime]::UtcNow.AddSeconds($BootTimeoutSeconds)
do {
    $bootCompleted = (& $adb -s $Serial shell getprop sys.boot_completed 2>$null).Trim()
    if ($bootCompleted -eq '1') {
        break
    }
    Start-Sleep -Seconds 1
} until ([DateTime]::UtcNow -ge $bootDeadline)

if ($bootCompleted -ne '1') {
    throw "Android did not finish booting within $BootTimeoutSeconds seconds: $Serial"
}

Push-Location $repositoryRoot
try {
    & cargo build -p gpsx-transport --bin gpsx-discover
    if ($LASTEXITCODE -ne 0) {
        throw 'Discovery bridge build failed.'
    }
} finally {
    Pop-Location
}

$networkBridgePid = $null
$listener = Get-NetTCPConnection -LocalAddress '127.0.0.1' -LocalPort 49302 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    $listenerProcess = Get-Process -Id $listener.OwningProcess -ErrorAction Stop
    if ($listenerProcess.Path -ne $relay) {
        throw "TCP port 49302 is already used by another program: $($listenerProcess.Path)"
    }
    $networkBridgePid = $listenerProcess.Id
} else {
    $networkBridge = Start-Process `
        -FilePath $relay `
        -ArgumentList '--android-relay' `
        -WorkingDirectory $repositoryRoot `
        -WindowStyle Hidden `
        -PassThru

    Start-Sleep -Milliseconds 500
    if ($networkBridge.HasExited) {
        throw 'Network discovery bridge failed to start.'
    }
    $networkBridgePid = $networkBridge.Id
}

$gamepadBridgePid = $null
$existingGamepadBridge = Get-CimInstance Win32_Process -Filter "Name = 'scrcpy.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match [regex]::Escape($Serial) -and $_.CommandLine -match 'gamepad=uhid' } |
    Select-Object -First 1

if ($existingGamepadBridge -and -not $HideGamepadBridge -and $existingGamepadBridge.CommandLine -match '--no-video') {
    Stop-Process -Id $existingGamepadBridge.ProcessId -Force
    $existingGamepadBridge = $null
}

if ($existingGamepadBridge) {
    $gamepadBridgePid = [int]$existingGamepadBridge.ProcessId
} else {
    $gamepadArguments =
        if ($HideGamepadBridge) {
            @(
                '--serial', $Serial,
                '--no-video', '--no-audio',
                '--keyboard=disabled', '--mouse=disabled',
                '--gamepad=uhid'
            )
        } else {
            @(
                '--serial', $Serial,
                '--no-audio',
                '--keyboard=disabled', '--mouse=disabled',
                '--gamepad=uhid',
                '--window-title', 'GPSx-Xbox-Bridge'
            )
        }

    $gamepadStart = @{
        FilePath = $scrcpy
        ArgumentList = $gamepadArguments
        WorkingDirectory = (Split-Path -Parent $scrcpy)
        PassThru = $true
    }
    if ($HideGamepadBridge) {
        $gamepadStart.WindowStyle = 'Hidden'
    }
    $gamepadBridge = Start-Process @gamepadStart

    Start-Sleep -Seconds 2
    if ($gamepadBridge.HasExited) {
        throw 'UHID gamepad bridge failed to start.'
    }
    $gamepadBridgePid = $gamepadBridge.Id
}

[pscustomobject]@{
    Emulator = $Serial
    NetworkBridgePid = $networkBridgePid
    GamepadBridgePid = $gamepadBridgePid
    StopCommand = "Stop-Process -Id $networkBridgePid,$gamepadBridgePid"
}
