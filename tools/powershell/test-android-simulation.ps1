# SPDX-License-Identifier: AGPL-3.0-only

[CmdletBinding()]
param(
    [string]$AvdName = 'GPSx_GoogleTV_API36',
    [switch]$KeepEmulator,
    [string]$TcpdumpPath = '',
    [ValidateRange(1, 1000)]
    [int]$Iterations = 1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sdkRoot = Join-Path $repositoryRoot '.tools\android-sdk'
$ndkVersion = '28.2.13676358'
$ndkRoot = Join-Path $sdkRoot "ndk\$ndkVersion"
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$clang = Join-Path $ndkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin\x86_64-linux-android24-clang.cmd'
$ar = Join-Path $ndkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-ar.exe'
$fakeConsole = Join-Path $repositoryRoot 'crates\gpsx-transport\tests\support\fake_console.py'
$remoteTest = '/data/local/tmp/gpsx-android-virtual-lan-test'
$serial = $null
$emulatorProcess = $null
$serverProcess = $null
$startedEmulator = $false

function Assert-LastExitCode([string]$Operation) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Operation failed with exit code $LASTEXITCODE"
    }
}

function Find-RunningEmulator {
    $line = (& $adb devices) | Where-Object { $_ -match '^(emulator-\d+)\s+device$' } | Select-Object -First 1
    if ($null -eq $line) {
        return $null
    }
    return ([regex]::Match($line, '^(emulator-\d+)')).Groups[1].Value
}

foreach ($required in @($adb, $emulator, $clang, $ar, $fakeConsole)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing Android simulation prerequisite: $required"
    }
}

$installedTargets = & rustup +1.85.0 target list --installed
Assert-LastExitCode 'rustup target inspection'
if ($installedTargets -notcontains 'x86_64-linux-android') {
    throw 'Missing Rust target x86_64-linux-android for toolchain 1.85.0'
}

$python = Get-Command python.exe -ErrorAction Stop
$logDirectory = Join-Path $repositoryRoot 'target\android-simulation'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$runResults = @()

try {
    $serial = Find-RunningEmulator
    if ($null -eq $serial) {
        $emulatorArguments = @(
            '-avd', $AvdName,
            '-no-window',
            '-no-audio',
            '-no-boot-anim',
            '-gpu', 'swiftshader_indirect',
            '-no-snapshot-save'
        )
        if (-not [string]::IsNullOrWhiteSpace($TcpdumpPath)) {
            $emulatorArguments += @('-tcpdump', $TcpdumpPath)
        }
        $emulatorProcess = Start-Process -FilePath $emulator -ArgumentList $emulatorArguments `
            -WindowStyle Hidden -PassThru
        $startedEmulator = $true

        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            Start-Sleep -Seconds 1
            $serial = Find-RunningEmulator
            if ($null -ne $serial) {
                break
            }
            if ($emulatorProcess.HasExited) {
                throw "Android emulator exited with code $($emulatorProcess.ExitCode)"
            }
        }
        if ($null -eq $serial) {
            throw 'Android emulator did not appear in ADB within 60 seconds'
        }
    }

    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $bootCompleted = (& $adb -s $serial shell getprop sys.boot_completed 2>$null).Trim()
        if ($bootCompleted -eq '1') {
            break
        }
        Start-Sleep -Seconds 1
    }
    if ($bootCompleted -ne '1') {
        throw 'Android emulator did not finish booting within 90 seconds'
    }

    $abi = (& $adb -s $serial shell getprop ro.product.cpu.abi).Trim()
    Assert-LastExitCode 'Android emulator ABI query'
    if ($abi -ne 'x86_64') {
        throw "The selected emulator ABI is $abi; this gate requires x86_64"
    }

    $env:CC_x86_64_linux_android = $clang
    $env:AR_x86_64_linux_android = $ar
    $env:CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER = $clang
    & cargo +1.85.0 test -p gpsx-transport --test android_virtual_lan --target x86_64-linux-android --no-run --locked
    Assert-LastExitCode 'Android integration-test compilation'

    $binaryDirectory = Join-Path $repositoryRoot 'target\x86_64-linux-android\debug\deps'
    $binary = Get-ChildItem -LiteralPath $binaryDirectory -Filter 'android_virtual_lan-*' |
        Where-Object { $_.Extension -ne '.d' } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $binary) {
        throw 'Compiled Android integration-test binary was not found'
    }

    & $adb -s $serial push $binary.FullName $remoteTest
    Assert-LastExitCode 'ADB test-binary transfer'
    & $adb -s $serial shell chmod 755 $remoteTest
    Assert-LastExitCode 'ADB test-binary permission update'

    for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
        $serverOutput = Join-Path $logDirectory ('fake-console-{0:D3}.out.log' -f $iteration)
        $serverError = Join-Path $logDirectory ('fake-console-{0:D3}.err.log' -f $iteration)
        Set-Content -LiteralPath $serverOutput -Value ''
        Set-Content -LiteralPath $serverError -Value ''
        try {
            $serverProcess = Start-Process -FilePath $python.Source -ArgumentList @($fakeConsole) `
                -RedirectStandardOutput $serverOutput -RedirectStandardError $serverError `
                -WindowStyle Hidden -PassThru
            for ($attempt = 0; $attempt -lt 40; $attempt++) {
                if ((Get-Content -LiteralPath $serverOutput -Raw) -match 'GPSX_ANDROID_FAKE_CONSOLE_READY') {
                    break
                }
                if ($serverProcess.HasExited) {
                    throw "Fake console exited early on iteration $iteration`: $(Get-Content -LiteralPath $serverError -Raw)"
                }
                Start-Sleep -Milliseconds 250
            }
            if ((Get-Content -LiteralPath $serverOutput -Raw) -notmatch 'GPSX_ANDROID_FAKE_CONSOLE_READY') {
                throw "Fake console did not become ready within 10 seconds on iteration $iteration"
            }

            & $adb -s $serial shell "GPSX_ANDROID_SIM_HOST=10.0.2.2 RUST_BACKTRACE=1 $remoteTest --ignored --exact android_virtual_lan_discovers_and_opens_ctrl_to_fake_console --nocapture"
            Assert-LastExitCode "Android virtual-LAN integration test iteration $iteration"

            $serverExited = $serverProcess.WaitForExit(5000)
            if (-not $serverExited) {
                throw "Fake console did not exit within 5 seconds after iteration $iteration"
            }
            $serverProcess.WaitForExit()
            $serverFailure = Get-Content -LiteralPath $serverError -Raw
            if (-not [string]::IsNullOrWhiteSpace($serverFailure)) {
                throw "Fake console reported an error on iteration $iteration`: $serverFailure"
            }
            $runResults += [pscustomobject]@{
                iteration = $iteration
                status = 'PASS'
                stdout = [IO.Path]::GetFileName($serverOutput)
                stdout_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverOutput).Hash.ToLowerInvariant()
                stderr = [IO.Path]::GetFileName($serverError)
                stderr_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverError).Hash.ToLowerInvariant()
            }
            Write-Host "FACT: Android x86_64 emulator discovery and ctrl handshake simulation iteration $iteration/$Iterations PASS."
        }
        finally {
            if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
                Stop-Process -Id $serverProcess.Id -Force
            }
            $serverProcess = $null
        }
    }
    $summaryPath = Join-Path $logDirectory 'run-summary.json'
    [pscustomobject]@{
        iterations_requested = $Iterations
        iterations_passed = $runResults.Count
        results = $runResults
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $summaryPath -Encoding utf8
    Write-Host "FACT: retained Android simulation evidence for $($runResults.Count)/$Iterations passing iterations at $summaryPath"
}
finally {
    if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
        Stop-Process -Id $serverProcess.Id -Force
    }
    if ($null -ne $serial) {
        & $adb -s $serial shell rm -f $remoteTest 2>$null | Out-Null
        if ($startedEmulator -and -not $KeepEmulator) {
            & $adb -s $serial emu kill 2>$null | Out-Null
        }
    }
}
