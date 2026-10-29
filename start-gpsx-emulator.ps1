[CmdletBinding()]
param(
    [switch]$NoBuild,
    [string]$Avd = 'GPSx_GoogleTV_API36',
    [switch]$HideGamepadBridge
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = $PSScriptRoot
$bridgeLauncher = Join-Path $repositoryRoot 'tools\powershell\start-android-emulator-bridges.ps1'
$androidApp = Join-Path $repositoryRoot 'apps\android-tv'
$gradle = Join-Path $androidApp 'gradlew.bat'
$adb = Join-Path $repositoryRoot '.tools\android-sdk\platform-tools\adb.exe'
$apk = Join-Path $androidApp 'app\build\outputs\apk\debug\app-debug.apk'

foreach ($required in @($bridgeLauncher, $gradle, $adb)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required file is missing: $required"
    }
}

Write-Host 'Starting the Google TV emulator and GPSx bridges...'
$bridgeParameters = @{ Avd = $Avd }
if ($HideGamepadBridge) {
    $bridgeParameters.HideGamepadBridge = $true
}
$bridge = & $bridgeLauncher @bridgeParameters
if (-not $bridge -or -not $bridge.Emulator) {
    throw 'The emulator bridge launcher returned no ready emulator.'
}

if (-not $NoBuild) {
    Write-Host 'Building the GPSx debug APK...'
    Push-Location $androidApp
    try {
        & $gradle :app:assembleDebug --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw 'Android APK build failed.'
        }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
    throw "GPSx APK is missing: $apk"
}

Write-Host "Installing GPSx on $($bridge.Emulator)..."
& $adb -s $bridge.Emulator install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw 'GPSx APK installation failed.'
}

Write-Host 'Launching GPSx...'
& $adb -s $bridge.Emulator shell am start -n com.gpsx.tv/.MainActivity
if ($LASTEXITCODE -ne 0) {
    throw 'GPSx launch failed.'
}

[pscustomobject]@{
    Emulator = $bridge.Emulator
    NetworkBridgePid = $bridge.NetworkBridgePid
    GamepadBridgePid = $bridge.GamepadBridgePid
    Apk = $apk
    StopBridges = $bridge.StopCommand
}
