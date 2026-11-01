[CmdletBinding()]
param(
    [string]$Serial = 'emulator-5554',
    [ValidateRange(1, 60)]
    [int]$Minutes = 15
)

# Makes the emulator's local PSN sign-in page reachable from a phone on the same Wi-Fi:
# PC LAN address:47800 -> adb forward 127.0.0.1:47801 -> emulator guest port 47800.
# The emulator shows this PC address in its QR code (property debug.gpsx.lan_host).
# Runs in the foreground for at most $Minutes, then removes the forward and the property.

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$adb = Join-Path $repositoryRoot '.tools\android-sdk\platform-tools\adb.exe'
$guestPort = 47800
$forwardPort = 47801

if ($Serial -notmatch '^emulator-[0-9]+$') {
    throw "Only an Android emulator serial is allowed: $Serial"
}

$route = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction Stop | Sort-Object RouteMetric, InterfaceMetric | Select-Object -First 1
$lanIp = (Get-NetIPAddress -InterfaceIndex $route.InterfaceIndex -AddressFamily IPv4 -ErrorAction Stop | Select-Object -First 1).IPAddress
if (-not $lanIp) {
    throw 'No IPv4 address found on the default-route interface.'
}

& $adb -s $Serial forward "tcp:$forwardPort" "tcp:$guestPort" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'adb forward failed.' }
& $adb -s $Serial shell setprop debug.gpsx.lan_host $lanIp
if ($LASTEXITCODE -ne 0) { throw 'Setting debug.gpsx.lan_host failed.' }

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse($lanIp), $guestPort)
$listener.Start()
Write-Host "PSN sign-in relay: http://${lanIp}:$guestPort -> $Serial port $guestPort for $Minutes min. Ctrl+C to stop."
Write-Host 'Windows may ask once to allow PowerShell on private networks: accept it.'

$pairs = [System.Collections.Generic.List[object]]::new()
$deadline = [DateTime]::UtcNow.AddMinutes($Minutes)
try {
    while ([DateTime]::UtcNow -lt $deadline) {
        while ($listener.Pending()) {
            $client = $listener.AcceptTcpClient()
            $upstream = [System.Net.Sockets.TcpClient]::new()
            try {
                $upstream.Connect('127.0.0.1', $forwardPort)
            } catch {
                $client.Close()
                $upstream.Close()
                continue
            }
            $pairs.Add([pscustomobject]@{
                Client = $client
                Upstream = $upstream
                Up = $client.GetStream().CopyToAsync($upstream.GetStream())
                Down = $upstream.GetStream().CopyToAsync($client.GetStream())
                Started = [DateTime]::UtcNow
            })
        }
        foreach ($pair in @($pairs)) {
            if ($pair.Up.IsCompleted -or $pair.Down.IsCompleted -or ([DateTime]::UtcNow - $pair.Started).TotalSeconds -gt 30) {
                $pair.Client.Close()
                $pair.Upstream.Close()
                [void]$pairs.Remove($pair)
            }
        }
        Start-Sleep -Milliseconds 50
    }
} finally {
    foreach ($pair in $pairs) {
        $pair.Client.Close()
        $pair.Upstream.Close()
    }
    $listener.Stop()
    & $adb -s $Serial forward --remove "tcp:$forwardPort" 2>$null | Out-Null
    & $adb -s $Serial shell setprop debug.gpsx.lan_host "''" 2>$null
    Write-Host 'PSN sign-in relay stopped.'
}
