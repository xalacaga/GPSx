[CmdletBinding()]
param(
    # Write UTF-8 without BOM (Windows PowerShell 5.1 `Set-Content -Encoding utf8` adds one).
    [string]$OutFile = ''
)

$ErrorActionPreference = 'Stop'
$os = Get-CimInstance Win32_OperatingSystem
$computer = Get-CimInstance Win32_ComputerSystem
$video = Get-CimInstance Win32_VideoController

$json = [ordered]@{
    schema_version = 1
    captured_at_utc = [DateTime]::UtcNow.ToString('o')
    os_caption = $os.Caption
    os_version = $os.Version
    architecture = $os.OSArchitecture
    memory_bytes = [uint64]$computer.TotalPhysicalMemory
    gpu = @($video | ForEach-Object {
        [ordered]@{
            name = $_.Name
            driver_version = $_.DriverVersion
        }
    })
} | ConvertTo-Json -Depth 4

if ($OutFile) {
    [System.IO.File]::WriteAllText($OutFile, $json + "`n", (New-Object System.Text.UTF8Encoding $false))
}
else {
    $json
}

