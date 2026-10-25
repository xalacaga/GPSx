[CmdletBinding()]
param(
    # Fail instead of warning when the Android target is not installed (CI should set it).
    [switch]$RequireAndroid
)

$ErrorActionPreference = 'Stop'

function Invoke-ExternalChecked {
    param(
        [Parameter(Mandatory)]
        [string]$FilePath,
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "External command failed ($LASTEXITCODE): $FilePath $($Arguments -join ' ')"
    }
}

$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$pythonTools = Join-Path $repositoryRoot 'tools\python'
$cargoCommand = Get-Command cargo -ErrorAction SilentlyContinue
$cargoPath = if ($cargoCommand) {
    $cargoCommand.Source
}
else {
    Join-Path $env:USERPROFILE '.cargo\bin\cargo.exe'
}
if (-not (Test-Path -LiteralPath $cargoPath)) {
    throw 'cargo is not installed. Install Rust with rustup before running the checks.'
}

if (-not (Get-Command link.exe -ErrorAction SilentlyContinue)) {
    $vswhere = 'C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe'
    if (-not (Test-Path -LiteralPath $vswhere)) {
        throw 'The MSVC Build Tools were not found.'
    }
    $env:PATH = "$(Split-Path -Parent $vswhere);$env:PATH"
    $vsPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    $developerShell = Join-Path $vsPath 'Common7\Tools\Launch-VsDevShell.ps1'
    & $developerShell -Arch amd64 -HostArch amd64 -SkipAutomaticLocation
}

Push-Location $repositoryRoot
try {
    Invoke-ExternalChecked -FilePath $cargoPath -Arguments @('fmt', '--all', '--check')
    Invoke-ExternalChecked -FilePath $cargoPath -Arguments @('clippy', '--workspace', '--all-targets', '--', '-D', 'warnings')
    Invoke-ExternalChecked -FilePath $cargoPath -Arguments @('test', '--workspace')

    # The portable core must never depend on platform crates (AGENTS.md).
    $corePackages = @('gpsx-model', 'gpsx-input', 'gpsx-telemetry')
    $treeArguments = @('tree', '--edges', 'normal,build', '--prefix', 'none') + ($corePackages | ForEach-Object { '-p', $_ })
    $tree = & $cargoPath @treeArguments
    if ($LASTEXITCODE -ne 0) {
        throw "cargo tree failed ($LASTEXITCODE)."
    }
    $forbidden = @($tree | Where-Object { $_ -match '^(jni|jni-sys|ndk|ndk-sys|ndk-context|android[\w-]*|windows|windows-sys|winapi)\s+v' })
    if ($forbidden.Count -gt 0) {
        throw "Portable core depends on platform crates: $($forbidden -join ', ')"
    }

    # The core must also build for Android. The target needs a one-time `rustup target add`.
    $androidTarget = 'aarch64-linux-android'
    $rustupCommand = Get-Command rustup -ErrorAction SilentlyContinue
    $rustupPath = if ($rustupCommand) { $rustupCommand.Source } else { Join-Path $env:USERPROFILE '.cargo\bin\rustup.exe' }
    $installedTargets = if (Test-Path -LiteralPath $rustupPath) { & $rustupPath target list --installed } else { @() }
    if ($installedTargets -contains $androidTarget) {
        Invoke-ExternalChecked -FilePath $cargoPath -Arguments (@('check', '--target', $androidTarget) + ($corePackages | ForEach-Object { '-p', $_ }))
    }
    elseif ($RequireAndroid) {
        throw "Target $androidTarget is not installed; run: rustup target add $androidTarget"
    }
    else {
        Write-Warning "NOT_TESTED: Android cross-check skipped because $androidTarget is not installed (use -RequireAndroid to make this fatal)."
    }

    $env:PYTHONPATH = $pythonTools
    $pythonPath = (Get-Command python -ErrorAction Stop).Source
    $pythonVersionText = (& $pythonPath --version 2>&1 | Out-String).Trim()
    if ($pythonVersionText -notmatch '^Python (\d+)\.(\d+)' -or [version]"$($Matches[1]).$($Matches[2])" -lt [version]'3.12') {
        throw "Python 3.12+ is required (found: $pythonVersionText)."
    }
    Invoke-ExternalChecked -FilePath $pythonPath -Arguments @('-m', 'unittest', 'discover', '-s', 'tools/python/tests', '-v')
    Invoke-ExternalChecked -FilePath $pythonPath -Arguments @('-m', 'gpsx_evidence.cli', 'scan', 'tests/fixtures')
    Invoke-ExternalChecked -FilePath $pythonPath -Arguments @('-m', 'gpsx_evidence.cli', 'manifest-verify', 'tests/fixtures', 'tests/fixtures/manifest.json')
}
finally {
    Pop-Location
}

