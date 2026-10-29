[CmdletBinding()]
param(
    [switch]$NoBuild,
    [string]$Avd = 'GPSx_GoogleTV_API36',
    [switch]$HideGamepadBridge
)

$ErrorActionPreference = 'Stop'
$launcher = (Resolve-Path (Join-Path $PSScriptRoot '..\..\start-gpsx-emulator.ps1')).Path
& $launcher @PSBoundParameters
