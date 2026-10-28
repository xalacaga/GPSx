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

function Resolve-PinnedAndroidNdk {
    param(
        [Parameter(Mandatory)]
        [string]$Version,
        [Parameter(Mandatory)]
        [string[]]$Candidates
    )

    foreach ($candidate in @($Candidates | Select-Object -Unique)) {
        $propertiesPath = Join-Path $candidate 'source.properties'
        if (-not (Test-Path -LiteralPath $propertiesPath)) {
            continue
        }
        $properties = Get-Content -Raw -LiteralPath $propertiesPath
        if ($properties -match "(?m)^Pkg\.Revision\s*=\s*$([regex]::Escape($Version))\s*$") {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

function Get-PinnedAndroidNdk {
    param(
        [Parameter(Mandatory)]
        [string]$Version,
        [Parameter(Mandatory)]
        [string[]]$Candidates
    )

    $resolved = Resolve-PinnedAndroidNdk -Version $Version -Candidates $Candidates
    if (-not $resolved) {
        throw "Android NDK $Version was not found. Set ANDROID_NDK_HOME to that exact NDK or install it under ANDROID_SDK_ROOT/ndk/$Version (repository-local .tools/android-sdk is also checked)."
    }
    return $resolved
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

    $coreCrates = @('gpsx-model', 'gpsx-input', 'gpsx-protocol', 'gpsx-telemetry', 'gpsx-crypto')

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

    $cryptoPackage = @($metadata.packages | Where-Object { $_.name -eq 'gpsx-crypto' })
    if ($cryptoPackage.Count -ne 1) {
        throw 'gpsx-crypto must appear exactly once in Cargo metadata.'
    }
    if (@($cryptoPackage[0].targets | Where-Object { $_.kind -contains 'custom-build' }).Count -ne 0 -or
        (Test-Path -LiteralPath (Join-Path $repositoryRoot 'crates\gpsx-crypto\build.rs'))) {
        throw 'gpsx-crypto must not have a build script.'
    }
    $expectedCryptoDependencies = @{
        'aes' = @{ Requirement = '=0.8.4'; Features = @('zeroize') }
        'aes-gcm' = @{ Requirement = '=0.10.3'; Features = @('aes', 'zeroize') }
        'cfb-mode' = @{ Requirement = '=0.8.2'; Features = @('zeroize') }
        'ctr' = @{ Requirement = '=0.9.2'; Features = @('zeroize') }
        'hmac' = @{ Requirement = '=0.12.1'; Features = @() }
        'rand_core' = @{ Requirement = '=0.6.4'; Features = @() }
        'secp256k1' = @{ Requirement = '=0.33.1'; Features = @('alloc') }
        'sha2' = @{ Requirement = '=0.10.9'; Features = @() }
        'subtle' = @{ Requirement = '=2.6.1'; Features = @() }
        'zeroize' = @{ Requirement = '=1.9.1'; Features = @() }
    }
    $actualCryptoDependencies = @($cryptoPackage[0].dependencies | Where-Object { $_.kind -eq $null })
    if ($actualCryptoDependencies.Count -ne $expectedCryptoDependencies.Count) {
        throw 'gpsx-crypto dependency count differs from the reviewed graph.'
    }
    foreach ($dependency in $actualCryptoDependencies) {
        if (-not $expectedCryptoDependencies.ContainsKey($dependency.name)) {
            throw "gpsx-crypto has an unreviewed dependency: $($dependency.name)"
        }
        $expected = $expectedCryptoDependencies[$dependency.name]
        if ($dependency.req -ne $expected.Requirement -or $dependency.uses_default_features -or
            (Compare-Object @($dependency.features) @($expected.Features))) {
            throw "gpsx-crypto dependency declaration differs from review: $($dependency.name)"
        }
    }

    $registrationAuditFixture = Get-Content -Raw -LiteralPath (Join-Path $repositoryRoot 'tests\fixtures\registration_crypto_dependency_audit_v1.json') | ConvertFrom-Json
    $auditFixture = Get-Content -Raw -LiteralPath (Join-Path $repositoryRoot 'tests\fixtures\session_crypto_dependency_audit_v1.json') | ConvertFrom-Json
    $expectedRegistryPackages = @($auditFixture.packages | ForEach-Object { "$($_.name)|$($_.version)|$($_.checksum)" } | Sort-Object)
    $registrationRegistryPackages = @($registrationAuditFixture.packages | ForEach-Object { "$($_.name)|$($_.version)|$($_.checksum)" } | Sort-Object)
    $lockText = Get-Content -Raw -LiteralPath (Join-Path $repositoryRoot 'Cargo.lock')
    $readRegistryPackages = {
        param([string]$Text)
        @([regex]::Matches($Text, '(?ms)^\[\[package\]\]\r?\n(?<body>.*?)(?=^\[\[package\]\]|\z)') | ForEach-Object {
        $body = $_.Groups['body'].Value
        if ($body -notmatch '(?m)^source = "registry\+[^\"]+"\r?$') {
            return
        }
        if ($body -notmatch '(?m)^name = "(?<name>[^"]+)"\r?$') { throw 'Cargo.lock registry package lacks a name.' }
        $name = $Matches['name']
        if ($body -notmatch '(?m)^version = "(?<version>[^"]+)"\r?$') { throw "Cargo.lock package $name lacks a version." }
        $version = $Matches['version']
        if ($body -notmatch '(?m)^checksum = "(?<checksum>[0-9a-f]{64})"\r?$') { throw "Cargo.lock package $name lacks a checksum." }
        "$name|$version|$($Matches['checksum'])"
        } | Sort-Object)
    }
    $actualRegistryPackages = @(& $readRegistryPackages $lockText)
    $crlfRegistryPackages = @(& $readRegistryPackages ($lockText -replace '\r?\n', "`r`n"))
    if ($crlfRegistryPackages.Count -ne $expectedRegistryPackages.Count -or
        (Compare-Object $actualRegistryPackages $crlfRegistryPackages)) {
        throw "Cargo.lock CRLF parser regression: expected the same $($expectedRegistryPackages.Count) registry packages."
    }
    if (Compare-Object $actualRegistryPackages $expectedRegistryPackages) {
        throw 'Repository registry packages differ from the reviewed session dependency audit fixture.'
    }
    foreach ($registrationPackage in $registrationRegistryPackages) {
        if ($actualRegistryPackages -notcontains $registrationPackage) {
            throw "The reviewed registration dependency disappeared or changed: $registrationPackage"
        }
    }

    $denyConfigPath = Join-Path $repositoryRoot 'deny.toml'
    $assertDenyWildcardPolicy = {
        param([string]$Text)
        if ($Text -notmatch '(?m)^wildcards\s*=\s*"deny"\s*$' -or
            $Text -notmatch '(?m)^allow-wildcard-paths\s*=\s*true\s*$') {
            throw 'cargo-deny must reject registry wildcards while allowing workspace path wildcards.'
        }
    }
    $denyConfig = Get-Content -Raw -LiteralPath $denyConfigPath
    & $assertDenyWildcardPolicy $denyConfig
    $hostileDenyConfig = $denyConfig -replace '(?m)^wildcards\s*=\s*"deny"\s*$', 'wildcards = "allow"'
    $hostileDenyRejected = $false
    try {
        & $assertDenyWildcardPolicy $hostileDenyConfig
    }
    catch {
        $hostileDenyRejected = $true
    }
    if (-not $hostileDenyRejected) {
        throw 'Hostile cargo-deny wildcard-policy self-test was not rejected.'
    }

    $wrapperPropertiesPath = Join-Path $repositoryRoot 'apps\android-tv\gradle\wrapper\gradle-wrapper.properties'
    $wrapperJarPath = Join-Path $repositoryRoot 'apps\android-tv\gradle\wrapper\gradle-wrapper.jar'
    $wrapperProperties = Get-Content -Raw -LiteralPath $wrapperPropertiesPath
    $expectedDistributionHash = 'bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01'
    if ($wrapperProperties -notmatch "(?m)^distributionSha256Sum=$expectedDistributionHash\r?$") {
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
    Invoke-ExternalChecked -FilePath $cargoPath -Arguments @('audit', '--file', 'fuzz/Cargo.lock')
    Invoke-ExternalChecked -FilePath $cargoPath -Arguments @('deny', '--manifest-path', 'fuzz/Cargo.toml', '--locked', 'check')

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

    $findReviewedCryptoDuplicates = {
        param([string[]]$Lines)
        $reviewedFamilies = @('aes', 'cipher', 'digest')
        foreach ($family in $reviewedFamilies) {
            $versions = @($Lines | ForEach-Object {
                if ($_ -match "^$([regex]::Escape($family))\s+v(?<version>[^\s]+)") {
                    $Matches['version']
                }
            } | Sort-Object -Unique)
            if ($versions.Count -gt 1) {
                "$family|$($versions -join ',')"
            }
        }
    }
    $hostileDuplicates = @(& $findReviewedCryptoDuplicates @('cipher v0.4.4', 'cipher v0.5.0'))
    if ($hostileDuplicates.Count -ne 1 -or $hostileDuplicates[0] -ne 'cipher|0.4.4,0.5.0') {
        throw 'Hostile duplicate-cipher self-test did not detect the simulated split generation.'
    }
    $duplicateTree = & $cargoPath tree -p gpsx-crypto -d --prefix none --locked
    if ($LASTEXITCODE -ne 0) {
        throw "cargo tree duplicate check failed ($LASTEXITCODE)."
    }
    $reviewedDuplicates = @(& $findReviewedCryptoDuplicates @($duplicateTree))
    if ($reviewedDuplicates.Count -gt 0) {
        throw "gpsx-crypto has a forbidden duplicate generation: $($reviewedDuplicates -join '; ')"
    }

    # The core must also build for Android. The target needs a one-time `rustup target add`.
    $androidTarget = 'aarch64-linux-android'
    $rustupCommand = Get-Command rustup -ErrorAction SilentlyContinue
    $rustupPath = if ($rustupCommand) { $rustupCommand.Source } else { Join-Path $env:USERPROFILE '.cargo\bin\rustup.exe' }
    $installedTargets = if (Test-Path -LiteralPath $rustupPath) { & $rustupPath target list --installed } else { @() }
    if ($installedTargets -contains $androidTarget) {
        $pinnedNdkVersion = '28.2.13676358'
        $androidApi = 24
        if ($auditFixture.android_ndk -ne $pinnedNdkVersion -or
            [int]$auditFixture.android_api -ne $androidApi -or
            $auditFixture.android_c_compiler -ne "aarch64-linux-android$androidApi-clang") {
            throw 'The Android NDK/API/compiler selection differs from the reviewed session dependency audit fixture.'
        }
        $ndkCandidates = @()
        if ($env:ANDROID_NDK_HOME) {
            $ndkCandidates += $env:ANDROID_NDK_HOME
        }
        if ($env:ANDROID_NDK_ROOT) {
            $ndkCandidates += $env:ANDROID_NDK_ROOT
        }
        foreach ($sdkRoot in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $repositoryRoot '.tools\android-sdk'))) {
            if ($sdkRoot) {
                $ndkCandidates += Join-Path $sdkRoot "ndk\$pinnedNdkVersion"
            }
        }

        $missingNdkRejected = $false
        try {
            Get-PinnedAndroidNdk -Version $pinnedNdkVersion -Candidates @(
                (Join-Path $repositoryRoot '.missing-ndk-self-test')
            ) | Out-Null
        }
        catch {
            if ($_.Exception.Message -notlike "Android NDK $pinnedNdkVersion was not found.*") {
                throw
            }
            $missingNdkRejected = $true
        }
        if (-not $missingNdkRejected) {
            throw 'Android NDK missing-installation self-test did not fail.'
        }

        $ndkRoot = Get-PinnedAndroidNdk -Version $pinnedNdkVersion -Candidates $ndkCandidates

        $llvmBin = Join-Path $ndkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin'
        $clangPath = Join-Path $llvmBin "aarch64-linux-android$androidApi-clang.cmd"
        $arPath = Join-Path $llvmBin 'llvm-ar.exe'
        if (-not (Test-Path -LiteralPath $clangPath) -or -not (Test-Path -LiteralPath $arPath)) {
            throw "Android NDK $pinnedNdkVersion is incomplete: expected $clangPath and $arPath."
        }
        $env:CC_aarch64_linux_android = $clangPath
        $env:AR_aarch64_linux_android = $arPath
        Write-Host "Android NDK: version=$pinnedNdkVersion api=$androidApi path=$ndkRoot"
        Invoke-ExternalChecked -FilePath $cargoPath -Arguments (@('check', '--locked', '--target', $androidTarget) + ($corePackages | ForEach-Object { '-p', $_ }))
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

