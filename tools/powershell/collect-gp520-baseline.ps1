[CmdletBinding()]
param(
    [string]$Adb = '',
    # Write UTF-8 without BOM (Windows PowerShell 5.1 `Set-Content -Encoding utf8` adds one).
    [string]$OutFile = ''
)

# Dormant until the owner opens the physical-hardware gate (docs/OFFLINE_FIRST.md).

$ErrorActionPreference = 'Stop'
if (-not $Adb) {
    $repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
    $projectAdb = Join-Path $repositoryRoot '.tools\platform-tools\adb.exe'
    $Adb = if (Test-Path -LiteralPath $projectAdb) { $projectAdb } else { 'adb' }
}
if (-not (Get-Command $Adb -ErrorAction SilentlyContinue)) {
    throw 'adb is not installed or is not available on PATH.'
}

$connectedDevices = @(& $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" })
if ($connectedDevices.Count -ne 1) {
    throw "Expected exactly one authorized ADB device, found $($connectedDevices.Count)."
}

function Invoke-AdbShell {
    param(
        [Parameter(Mandatory)]
        [string]$Command,
        # Optional probes record their failure instead of aborting the capture.
        [switch]$Optional
    )

    $output = @(& $Adb shell $Command)
    if ($LASTEXITCODE -ne 0) {
        if ($Optional) {
            return "UNAVAILABLE (exit $LASTEXITCODE): $Command"
        }
        throw "adb shell failed ($LASTEXITCODE): $Command"
    }
    return Remove-DeviceIdentifiers $output
}

function Remove-DeviceIdentifiers {
    param([string[]]$Lines)

    $text = $Lines -join "`n"
    $text = $text -replace '(?i)\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\b', '[REDACTED]'
    # /proc/bus/input/devices prefixes lines with a letter, e.g. "U: Uniq=...".
    $text = $text -replace '(?im)^(\s*(?:\w:\s*)?(?:Uniq|Serial Number|Descriptor)\s*[:=]\s*).+$', '$1[REDACTED]'
    return $text
}

$properties = [ordered]@{}
foreach ($name in @(
    'ro.product.manufacturer',
    'ro.product.model',
    'ro.product.device',
    'ro.build.fingerprint',
    'ro.build.display.id',
    'ro.build.version.release',
    'ro.build.version.sdk',
    'ro.product.cpu.abilist',
    'ro.hardware',
    'ro.opengles.version'
)) {
    $properties[$name] = ([string](Invoke-AdbShell "getprop $name")).Trim()
}

$capture = [ordered]@{
    schema_version = 2
    captured_at_utc = [DateTime]::UtcNow.ToString('o')
    properties = $properties
    kernel = Invoke-AdbShell 'uname -r'
    hid_drivers = Invoke-AdbShell 'ls /sys/bus/hid/drivers' -Optional
    key_layouts = Invoke-AdbShell 'ls /vendor/usr/keylayout /system/usr/keylayout /odm/usr/keylayout' -Optional
    # Per-device KeyLayoutFile and VID/PID/version: direct evidence of the Generic.kl fallback.
    input_dumpsys = Invoke-AdbShell 'dumpsys input'
    input_devices = Invoke-AdbShell 'cat /proc/bus/input/devices'
    input_event_codes = Invoke-AdbShell 'getevent -lp' -Optional
    features = Invoke-AdbShell 'pm list features'
    media_codecs = Invoke-AdbShell 'dumpsys media.codec'
    audio = Invoke-AdbShell 'dumpsys audio'
}

$json = $capture | ConvertTo-Json -Depth 6
if ($OutFile) {
    [System.IO.File]::WriteAllText($OutFile, $json + "`n", (New-Object System.Text.UTF8Encoding $false))
}
else {
    $json
}
