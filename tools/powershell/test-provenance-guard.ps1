[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'ProvenanceGuard.psm1') -Force

function New-TestRepository {
    param([switch]$UnderBuild)
    $container = Join-Path ([System.IO.Path]::GetTempPath()) ("gpsx-provenance-" + [guid]::NewGuid().ToString('N'))
    $root = if ($UnderBuild) { Join-Path $container 'build' } else { $container }
    New-Item -ItemType Directory -Path (Join-Path $root 'docs') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'crates\gpsx-model\src') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'tools\provenance') -Force | Out-Null
    @'
# Provenance ledger

## Derived-file entries

| Local path | Upstream path | Commit | Original copyright | SPDX | Owner/date | Modifications |
|---|---|---|---|---|---|---|
'@ | Set-Content -LiteralPath (Join-Path $root 'docs\PROVENANCE.md') -Encoding UTF8
    @'
[workspace]
members = ["crates/gpsx-model"]
'@ | Set-Content -LiteralPath (Join-Path $root 'Cargo.toml') -Encoding UTF8
    '[package]' | Set-Content -LiteralPath (Join-Path $root 'crates\gpsx-model\Cargo.toml') -Encoding UTF8
    '// SPDX-License-Identifier: AGPL-3.0-only' | Set-Content -LiteralPath (Join-Path $root 'crates\gpsx-model\src\lib.rs') -Encoding UTF8
    'lib/src/example.c' | Set-Content -LiteralPath (Join-Path $root 'tools\provenance\chiaki-ng-c8bea264-paths.txt') -Encoding UTF8
    return $root
}

function Enable-ProtocolTestCrate {
    param([string]$Root)
    New-Item -ItemType Directory -Path (Join-Path $Root 'crates\gpsx-protocol\src') -Force | Out-Null
    @'
[workspace]
members = ["crates/gpsx-model", "crates/gpsx-protocol"]
'@ | Set-Content -LiteralPath (Join-Path $Root 'Cargo.toml') -Encoding UTF8
    '[package]' | Set-Content -LiteralPath (Join-Path $Root 'crates\gpsx-protocol\Cargo.toml') -Encoding UTF8
    '// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL' | Set-Content -LiteralPath (Join-Path $Root 'crates\gpsx-protocol\src\lib.rs') -Encoding UTF8
}

function Add-DerivedRow {
    param([string]$Root, [string]$Row)
    Add-Content -LiteralPath (Join-Path $Root 'docs\PROVENANCE.md') -Value $Row
}

function Assert-Fails {
    param([scriptblock]$Action, [string]$Name)
    try {
        & $Action
    }
    catch {
        Write-Host "PASS hostile: $Name"
        return
    }
    throw "Expected provenance guard failure: $Name"
}

$roots = [System.Collections.Generic.List[string]]::new()
try {
    $narrativeOnly = New-TestRepository
    $roots.Add($narrativeOnly)
    Enable-ProtocolTestCrate -Root $narrativeOnly
    Add-Content -LiteralPath (Join-Path $narrativeOnly 'docs\PROVENANCE.md') -Value 'Narrative mention: crates/gpsx-protocol/src/lib.rs'
    Assert-Fails { Assert-GpsxProvenance -RepositoryRoot $narrativeOnly } 'narrative substring is not a ledger row'

    $unknownCrate = New-TestRepository
    $roots.Add($unknownCrate)
    New-Item -ItemType Directory -Path (Join-Path $unknownCrate 'crates\gpsx-takion\src') -Force | Out-Null
    'pub fn derived() {}' | Set-Content -LiteralPath (Join-Path $unknownCrate 'crates\gpsx-takion\src\lib.rs') -Encoding UTF8
    Assert-Fails { Assert-GpsxProvenance -RepositoryRoot $unknownCrate } 'undeclared protocol-like crate'

    $outsideHeader = New-TestRepository
    $roots.Add($outsideHeader)
    '// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL' | Set-Content -LiteralPath (Join-Path $outsideHeader 'crates\gpsx-model\src\derived.rs') -Encoding UTF8
    Assert-Fails { Assert-GpsxProvenance -RepositoryRoot $outsideHeader } 'derived SPDX outside protocol crates'

    $hiddenFile = New-TestRepository
    $roots.Add($hiddenFile)
    Enable-ProtocolTestCrate -Root $hiddenFile
    $commit = 'c8bea2642d0e95402f373af55584a3b98cd89417'
    Add-DerivedRow -Root $hiddenFile -Row "| crates/gpsx-protocol/Cargo.toml | N/A - GPSx-authored | $commit | GPSx | AGPL-3.0-only | GPSx / 2026-10-25 | Package manifest |"
    Add-DerivedRow -Root $hiddenFile -Row "| crates/gpsx-protocol/src/lib.rs | lib/src/example.c | $commit | upstream | LicenseRef-AGPL-3.0-only-OpenSSL | GPSx / 2026-10-25 | Rust adaptation |"
    $hiddenPath = Join-Path $hiddenFile 'crates\gpsx-protocol\src\hidden.rs'
    '// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL' | Set-Content -LiteralPath $hiddenPath -Encoding UTF8
    [System.IO.File]::SetAttributes($hiddenPath, [System.IO.FileAttributes]::Hidden)
    Assert-Fails { Assert-GpsxProvenance -RepositoryRoot $hiddenFile } 'hidden protocol file without ledger row'

    $outsideWorkspace = New-TestRepository
    $roots.Add($outsideWorkspace)
    New-Item -ItemType Directory -Path (Join-Path $outsideWorkspace 'protocol\takion') -Force | Out-Null
    '[package]' | Set-Content -LiteralPath (Join-Path $outsideWorkspace 'protocol\takion\Cargo.toml') -Encoding UTF8
    "[workspace]`nmembers = [`"crates/gpsx-model`", `"protocol/takion`"]" | Set-Content -LiteralPath (Join-Path $outsideWorkspace 'Cargo.toml') -Encoding UTF8
    Assert-Fails { Assert-GpsxProvenance -RepositoryRoot $outsideWorkspace } 'workspace member outside crates allow-list'

    $underBuild = New-TestRepository -UnderBuild
    $roots.Add($underBuild)
    New-Item -ItemType Directory -Path (Join-Path $underBuild 'tools') -Force | Out-Null
    '// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL' | Set-Content -LiteralPath (Join-Path $underBuild 'tools\takion.rs') -Encoding UTF8
    Assert-Fails { Assert-GpsxProvenance -RepositoryRoot $underBuild } 'repository located under a build directory'

    foreach ($extension in @('proto', 'java', 'json')) {
        $allText = New-TestRepository
        $roots.Add($allText)
        New-Item -ItemType Directory -Path (Join-Path $allText 'tools') -Force | Out-Null
        'LicenseRef-AGPL-3.0-only-OpenSSL' | Set-Content -LiteralPath (Join-Path $allText "tools\derived.$extension") -Encoding UTF8
        Assert-Fails { Assert-GpsxProvenance -RepositoryRoot $allText } "derived identifier in .$extension"
    }

    $wrongSpdx = New-TestRepository
    $roots.Add($wrongSpdx)
    Enable-ProtocolTestCrate -Root $wrongSpdx
    Add-DerivedRow -Root $wrongSpdx -Row "| crates/gpsx-protocol/Cargo.toml | N/A - GPSx-authored | $commit | GPSx | AGPL-3.0-only | GPSx / 2026-10-25 | Package manifest |"
    Add-DerivedRow -Root $wrongSpdx -Row "| crates/gpsx-protocol/src/lib.rs | lib/src/example.c | $commit | upstream | MIT | GPSx / 2026-10-25 | x |"
    Assert-Fails { Assert-GpsxProvenance -RepositoryRoot $wrongSpdx } 'incorrect derived SPDX value'

    $orphan = New-TestRepository
    $roots.Add($orphan)
    Add-DerivedRow -Root $orphan -Row "| crates/gpsx-protocol/src/missing.rs | lib/src/example.c | $commit | upstream | LicenseRef-AGPL-3.0-only-OpenSSL | GPSx / 2026-10-25 | x |"
    Assert-Fails { Assert-GpsxProvenance -RepositoryRoot $orphan } 'orphan ledger row'

    $valid = New-TestRepository
    $roots.Add($valid)
    Enable-ProtocolTestCrate -Root $valid
    Add-DerivedRow -Root $valid -Row "| crates/gpsx-protocol/src/lib.rs | lib/src/example.c | $commit | upstream | LicenseRef-AGPL-3.0-only-OpenSSL | GPSx / 2026-10-25 | Rust adaptation |"
    Add-DerivedRow -Root $valid -Row "| crates/gpsx-protocol/Cargo.toml | N/A - GPSx-authored | $commit | GPSx | AGPL-3.0-only | GPSx / 2026-10-25 | Package manifest |"
    Assert-GpsxProvenance -RepositoryRoot $valid
    Write-Host 'PASS valid: complete ledger rows'
}
finally {
    $temporaryBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\')
    foreach ($root in $roots) {
        if ($root -and (Test-Path -LiteralPath $root)) {
            $resolvedRoot = (Resolve-Path -LiteralPath $root).Path
            $expectedPrefix = "$temporaryBase\gpsx-provenance-"
            if (-not $resolvedRoot.StartsWith($expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing to remove unexpected test path: $resolvedRoot"
            }
            $relativeToTemp = $resolvedRoot.Substring($temporaryBase.Length + 1)
            $containerName = ($relativeToTemp -split '\\')[0]
            $cleanupRoot = Join-Path $temporaryBase $containerName
            Remove-Item -LiteralPath $cleanupRoot -Recurse -Force
        }
    }
}
