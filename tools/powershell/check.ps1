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
    $workspaceManifest = Get-Content -Raw -LiteralPath (Join-Path $repositoryRoot 'Cargo.toml')
    if ($workspaceManifest -notmatch '(?m)^license\s*=\s*"AGPL-3\.0-only"\s*$') {
        throw 'Cargo workspace package license must be AGPL-3.0-only.'
    }

    $noticePath = Join-Path $repositoryRoot 'NOTICE'
    $provenancePath = Join-Path $repositoryRoot 'docs\PROVENANCE.md'
    if (-not (Test-Path -LiteralPath $noticePath) -or -not (Test-Path -LiteralPath $provenancePath)) {
        throw 'NOTICE and docs/PROVENANCE.md are mandatory.'
    }

    Import-Module (Join-Path $PSScriptRoot 'ProvenanceGuard.psm1') -Force
    Assert-GpsxProvenance -RepositoryRoot $repositoryRoot
    Invoke-ExternalChecked -FilePath 'powershell.exe' -Arguments @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', (Join-Path $PSScriptRoot 'test-provenance-guard.ps1')
    )

    $coreCrates = @('gpsx-model', 'gpsx-input', 'gpsx-protocol', 'gpsx-telemetry')

    # CR3 authorizes gpsx-protocol only while it remains dependency-free and
    # has no build script. Cargo metadata includes normal, dev, and build deps.
    $metadataJson = & $cargoPath metadata --format-version 1 --no-deps
    if ($LASTEXITCODE -ne 0) {
        throw "cargo metadata failed ($LASTEXITCODE)."
    }
    $metadata = $metadataJson | ConvertFrom-Json
    $protocolPackage = @($metadata.packages | Where-Object { $_.name -eq 'gpsx-protocol' })
    if ($protocolPackage.Count -ne 1) {
        throw 'gpsx-protocol must appear exactly once in Cargo metadata.'
    }
    if (@($protocolPackage[0].dependencies).Count -ne 0) {
        throw 'gpsx-protocol must have zero normal, dev, and build dependencies.'
    }
    if (@($protocolPackage[0].targets | Where-Object { $_.kind -contains 'custom-build' }).Count -ne 0 -or
        (Test-Path -LiteralPath (Join-Path $repositoryRoot 'crates\gpsx-protocol\build.rs'))) {
        throw 'gpsx-protocol must not have a build script.'
    }

    $wrapperPropertiesPath = Join-Path $repositoryRoot 'apps\android-tv\gradle\wrapper\gradle-wrapper.properties'
    $wrapperJarPath = Join-Path $repositoryRoot 'apps\android-tv\gradle\wrapper\gradle-wrapper.jar'
    $wrapperProperties = Get-Content -Raw -LiteralPath $wrapperPropertiesPath
    $expectedDistributionHash = 'bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01'
    if ($wrapperProperties -notmatch "(?m)^distributionSha256Sum=$expectedDistributionHash$") {
        throw 'The Gradle distribution SHA-256 is missing or differs from the reviewed Gradle 9.6.0 checksum.'
    }
    $expectedWrapperJarHash = '497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7'
    $actualWrapperJarHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $wrapperJarPath).Hash.ToLowerInvariant()
    if ($actualWrapperJarHash -ne $expectedWrapperJarHash) {
        throw "Gradle wrapper JAR hash mismatch: $actualWrapperJarHash"
    }

    $workflowPath = Join-Path $repositoryRoot '.github\workflows\ci.yml'
    foreach ($workflowLine in Get-Content -LiteralPath $workflowPath) {
        if ($workflowLine -match '^\s*-\s+uses:\s+[^./][^@\s]+@([^\s#]+)') {
            if ($Matches[1] -notmatch '^[0-9a-f]{40}$') {
                throw "GitHub Action is not pinned to a full commit SHA: $($workflowLine.Trim())"
            }
        }
    }

    $requiredLegalStrings = @(
        'about_title',
        'legal_copyright',
        'legal_license',
        'legal_no_warranty',
        'legal_redistribution',
        'legal_source',
        'legal_build'
    )
    $androidResources = Join-Path $repositoryRoot 'apps\android-tv\app\src\main\res'
    $defaultStringFile = Join-Path $androidResources 'values\strings.xml'
    [xml]$defaultResources = Get-Content -Raw -LiteralPath $defaultStringFile
    $defaultNames = @($defaultResources.resources.string | ForEach-Object { $_.name })
    foreach ($requiredName in $requiredLegalStrings) {
        if ($defaultNames -notcontains $requiredName) {
            throw "Required legal string '$requiredName' is absent from $defaultStringFile"
        }
    }

    $qualifiedValuesDirectories = @(
        Get-ChildItem -LiteralPath $androidResources -Directory |
            Where-Object { $_.Name -like 'values-*' }
    )
    foreach ($valuesDirectory in $qualifiedValuesDirectories) {
        $qualifiedResourceFiles = @(Get-ChildItem -LiteralPath $valuesDirectory.FullName -File -Filter '*.xml')
        foreach ($qualifiedResourceFile in $qualifiedResourceFiles) {
            [xml]$qualifiedResources = Get-Content -Raw -LiteralPath $qualifiedResourceFile.FullName
            $qualifiedNames = @($qualifiedResources.resources.string | ForEach-Object { $_.name })
            foreach ($requiredName in $requiredLegalStrings) {
                if ($qualifiedNames -contains $requiredName) {
                    throw "Legal string '$requiredName' must be defined only in $defaultStringFile"
                }
            }
        }
    }

    Invoke-ExternalChecked -FilePath $cargoPath -Arguments @('fmt', '--all', '--check')
    Invoke-ExternalChecked -FilePath $cargoPath -Arguments @('clippy', '--workspace', '--all-targets', '--', '-D', 'warnings')
    Invoke-ExternalChecked -FilePath $cargoPath -Arguments @('test', '--workspace')

    # The portable core must never depend on platform crates (AGENTS.md).
    $corePackages = $coreCrates
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

