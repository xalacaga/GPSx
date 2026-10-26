Set-StrictMode -Version Latest

function Get-GpsxRelativePath {
    param([string]$Root, [string]$FullName)
    return $FullName.Substring($Root.Length + 1).Replace('\', '/')
}

function Test-GpsxGeneratedPath {
    param([string]$RelativePath)
    return $RelativePath -match '^(?:\.git|\.codex|\.tools|target|graphify-out|apps/android-tv/app/build|apps/android-tv/build)(?:/|$)' -or
        $RelativePath -match '(?:^|/)(?:\.gradle|\.kotlin)(?:/|$)'
}

function Get-GpsxRepositoryFiles {
    param([string]$Root)
    $pending = [System.Collections.Generic.Stack[System.IO.DirectoryInfo]]::new()
    $pending.Push([System.IO.DirectoryInfo]::new($Root))
    while ($pending.Count -gt 0) {
        $directory = $pending.Pop()
        foreach ($item in Get-ChildItem -LiteralPath $directory.FullName -Force) {
            $relative = Get-GpsxRelativePath -Root $Root -FullName $item.FullName
            if (Test-GpsxGeneratedPath -RelativePath $relative) {
                continue
            }
            if ($item -is [System.IO.DirectoryInfo]) {
                $pending.Push($item)
            }
            elseif ($item -is [System.IO.FileInfo]) {
                Write-Output $item
            }
        }
    }
}

function Assert-GpsxProvenance {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$RepositoryRoot
    )

    $root = (Resolve-Path -LiteralPath $RepositoryRoot).Path.TrimEnd('\')
    $provenancePath = Join-Path $root 'docs\PROVENANCE.md'
    $workspacePath = Join-Path $root 'Cargo.toml'
    $upstreamInventoryPath = Join-Path $root 'tools\provenance\chiaki-ng-c8bea264-paths.txt'
    foreach ($requiredPath in @($provenancePath, $workspacePath, $upstreamInventoryPath)) {
        if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
            throw "Required provenance input is missing: $requiredPath"
        }
    }
    $provenance = Get-Content -Raw -LiteralPath $provenancePath
    $pinnedCommit = 'c8bea2642d0e95402f373af55584a3b98cd89417'
    $coreCrates = @('gpsx-model', 'gpsx-input', 'gpsx-telemetry')
    $protocolCrates = @('gpsx-protocol', 'gpsx-transport', 'gpsx-crypto', 'gpsx-session')
    $allowedCrates = @($coreCrates + $protocolCrates)
    $allowedCratePaths = @($allowedCrates | ForEach-Object { "crates/$_" })
    $cratesRoot = Join-Path $root 'crates'

    $repositoryFiles = @(Get-GpsxRepositoryFiles -Root $root)

    $workspace = Get-Content -Raw -LiteralPath $workspacePath
    if ($workspace -notmatch '(?ms)^\[workspace\]\s*(.*?)(?=^\[|\z)') {
        throw 'Root Cargo.toml has no [workspace] table.'
    }
    $workspaceTable = $Matches[1]
    if ($workspaceTable -notmatch '(?ms)^\s*members\s*=\s*\[(.*?)\]') {
        throw 'Root Cargo.toml has no explicit workspace members array.'
    }
    $workspaceMembers = @([regex]::Matches($Matches[1], '"([^"]+)"') | ForEach-Object {
        $_.Groups[1].Value.Replace('\', '/').TrimEnd('/')
    })
    foreach ($member in $workspaceMembers) {
        if ($member -match '[*?]' -or $allowedCratePaths -notcontains $member) {
            throw "Undeclared or non-explicit workspace crate path: $member"
        }
        if (-not (Test-Path -LiteralPath (Join-Path $root "$member/Cargo.toml") -PathType Leaf)) {
            throw "Workspace member has no Cargo.toml: $member"
        }
    }

    foreach ($manifest in $repositoryFiles | Where-Object { $_.Name -eq 'Cargo.toml' }) {
        $relativeManifest = Get-GpsxRelativePath -Root $root -FullName $manifest.FullName
        if ($relativeManifest -eq 'Cargo.toml') {
            continue
        }
        $cratePath = $relativeManifest.Substring(0, $relativeManifest.Length - '/Cargo.toml'.Length)
        if ($allowedCratePaths -notcontains $cratePath -or $workspaceMembers -notcontains $cratePath) {
            throw "Cargo crate is outside the reviewed workspace allow-list: $cratePath"
        }
    }

    if (Test-Path -LiteralPath $cratesRoot) {
        foreach ($crateDirectory in Get-ChildItem -LiteralPath $cratesRoot -Directory -Force) {
            if ($allowedCrates -notcontains $crateDirectory.Name) {
                throw "Undeclared crate '$($crateDirectory.Name)'."
            }
        }
    }

    $approvedUpstreamPaths = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in Get-Content -LiteralPath $upstreamInventoryPath) {
        $candidate = $line.Trim()
        if ($candidate -and -not $candidate.StartsWith('#')) {
            if ($candidate -cnotmatch '^(lib|gui|test)/' -or $candidate -match '(^|/)\.\.(/|$)') {
                throw "Invalid path in pinned upstream inventory: $candidate"
            }
            [void]$approvedUpstreamPaths.Add($candidate)
        }
    }

    $entries = @{}
    $inTable = $false
    foreach ($line in $provenance -split "`r?`n") {
        if ($line -eq '## Derived-file entries') {
            $inTable = $true
            continue
        }
        if ($inTable -and $line -match '^##\s') {
            $inTable = $false
        }
        if (-not $inTable -or $line -notmatch '^\|') {
            continue
        }
        $cells = @(($line.Trim('|') -split '\|') | ForEach-Object { $_.Trim().Trim('`') })
        if ($cells.Count -ne 7 -or $cells[0] -eq 'Local path' -or $cells[0] -match '^-+$') {
            continue
        }
        if (@($cells | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
            throw "Every derived-file field must be filled: $line"
        }
        $localPath = $cells[0].Replace('\', '/')
        if ($entries.ContainsKey($localPath)) {
            throw "Duplicate derived-file entry: $localPath"
        }
        if ($cells[2] -ne $pinnedCommit) {
            throw "Unreviewed commit for $($localPath): $($cells[2])"
        }
        if (@($protocolCrates | Where-Object { $localPath.StartsWith("crates/$_/", [StringComparison]::Ordinal) }).Count -ne 1) {
            throw "Derived-file entry is outside an authorized protocol crate: $localPath"
        }
        if (-not (Test-Path -LiteralPath (Join-Path $root $localPath) -PathType Leaf)) {
            throw "Derived-file entry points to a missing local file: $localPath"
        }
        if ($cells[5] -notmatch '^.+\s/\s(\d{4}-\d{2}-\d{2})$') {
            throw "Owner/date must end with an ISO date for $localPath"
        }
        $parsedDate = [DateTime]::MinValue
        if (-not [DateTime]::TryParseExact($Matches[1], 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$parsedDate)) {
            throw "Invalid owner/date for $localPath"
        }
        $gpsxAuthored = $cells[1] -eq 'N/A - GPSx-authored'
        if ($gpsxAuthored) {
            if ($cells[4] -notin @('AGPL-3.0-only', 'LicenseRef-AGPL-3.0-only-OpenSSL')) {
                throw "Invalid SPDX for GPSx-authored protocol file: $localPath"
            }
        }
        else {
            if ($cells[4] -ne 'LicenseRef-AGPL-3.0-only-OpenSSL') {
                throw "Derived protocol file has the wrong SPDX value: $localPath"
            }
            $upstreamPaths = @(($cells[1] -split ';') | ForEach-Object { $_.Trim().Trim('`') })
            foreach ($upstreamPath in $upstreamPaths) {
                if (-not $approvedUpstreamPaths.Contains($upstreamPath)) {
                    throw "Upstream path is absent from the pinned inventory: $upstreamPath"
                }
            }
        }
        $entries[$localPath] = $cells
    }

    foreach ($crate in $protocolCrates) {
        $crateRoot = Join-Path $cratesRoot $crate
        if (-not (Test-Path -LiteralPath $crateRoot)) {
            continue
        }
        foreach ($file in Get-ChildItem -LiteralPath $crateRoot -File -Recurse -Force) {
            $relativePath = Get-GpsxRelativePath -Root $root -FullName $file.FullName
            if (-not $entries.ContainsKey($relativePath)) {
                throw "Protocol file has no complete derived-file row: $relativePath"
            }
            if ($file.Extension -eq '.rs') {
                $contents = Get-Content -Raw -LiteralPath $file.FullName
                if ($contents -notmatch '(?m)^// SPDX-License-Identifier: LicenseRef-AGPL-3\.0-only-OpenSSL\s*$') {
                    throw "Protocol Rust file lacks its derived-work SPDX header: $relativePath"
                }
            }
        }
    }

    $derivedIdentifier = 'LicenseRef-AGPL-3.0-only-OpenSSL'
    $complianceFiles = @(
        'LICENSE',
        'NOTICE',
        'tools/powershell/ProvenanceGuard.psm1',
        'tools/powershell/test-provenance-guard.ps1'
    )
    foreach ($source in $repositoryFiles) {
        $relativePath = Get-GpsxRelativePath -Root $root -FullName $source.FullName
        $authorized = @($protocolCrates | Where-Object {
            $relativePath.StartsWith("crates/$_/", [StringComparison]::Ordinal)
        }).Count -eq 1
        if ($authorized -or $source.Extension -eq '.md' -or $complianceFiles -contains $relativePath) {
            continue
        }
        try {
            $contents = Get-Content -Raw -LiteralPath $source.FullName -ErrorAction Stop
        }
        catch {
            continue
        }
        if ($contents.IndexOf($derivedIdentifier, [StringComparison]::Ordinal) -ge 0) {
            throw "Derived-work SPDX identifier appears outside an authorized protocol crate: $relativePath"
        }
    }
}

Export-ModuleMember -Function Assert-GpsxProvenance
