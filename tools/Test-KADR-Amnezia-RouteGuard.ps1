# PowerShell 5.1+ regression tests; no actual network settings are modified.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'KADR-Amnezia-RouteGuard.ps1'
$tokens = $null
$parseErrors = $null
[void][System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$parseErrors)
if (@($parseErrors).Count -ne 0) {
  throw ('PowerShell syntax errors: ' + (($parseErrors | ForEach-Object { $_.Message }) -join '; '))
}
. $scriptPath | Out-Null
$script:removed = @()
$script:privateAddresses = @(
  [pscustomobject]@{ InterfaceIndex = 11; IPAddress = '192.168.68.60'; AddressState='Preferred'; PrefixLength=22 },
  [pscustomobject]@{ InterfaceIndex = 52; IPAddress = '10.8.1.7'; AddressState='Preferred'; PrefixLength=32 },
  [pscustomobject]@{ InterfaceIndex = 12; IPAddress = '169.254.100.10'; AddressState='Preferred'; PrefixLength=16 }
)
$script:routes = @(
  [pscustomobject]@{ InterfaceIndex=52; DestinationPrefix='192.168.68.60/32'; NextHop='0.0.0.0' },
  [pscustomobject]@{ InterfaceIndex=52; DestinationPrefix='192.168.68.67/32'; NextHop='0.0.0.0' },
  [pscustomobject]@{ InterfaceIndex=52; DestinationPrefix='0.0.0.0/0'; NextHop='0.0.0.0' },
  [pscustomobject]@{ InterfaceIndex=11; DestinationPrefix='192.168.68.60/32'; NextHop='0.0.0.0' },
  [pscustomobject]@{ InterfaceIndex=35; DestinationPrefix='192.168.68.60/32'; NextHop='0.0.0.0' },
  [pscustomobject]@{ InterfaceIndex=52; DestinationPrefix='192.168.68.0/22'; NextHop='0.0.0.0' }
)
function Get-NetAdapter {
  [CmdletBinding()]param([switch]$Physical)
  if ($Physical) { return @([pscustomobject]@{ ifIndex=11; Name='Wi-Fi'; Status='Up' }) }
  return @(
    [pscustomobject]@{ ifIndex=11; Name='Wi-Fi'; Status='Up' },
    [pscustomobject]@{ ifIndex=52; Name='AmneziaVPN'; Status='Up' },
    [pscustomobject]@{ ifIndex=35; Name='outline-tap0'; Status='Up' }
  )
}
function Get-NetIPAddress {
  [CmdletBinding()]param([string]$AddressFamily)
  return $script:privateAddresses
}
function Get-NetRoute {
  [CmdletBinding()]param([string]$AddressFamily)
  return $script:routes
}
function Remove-NetRoute {
  [CmdletBinding(SupportsShouldProcess=$true)]
  param([Parameter(ValueFromPipeline=$true)]$InputObject)
  process { $script:removed += $InputObject.DestinationPrefix }
}
$found = @(Get-BadSelfRoutes)
if ($found.Count -ne 1 -or $found[0].DestinationPrefix -ne '192.168.68.60/32') {
  throw "Unsafe selection: expected 1 own-host Amnezia route, got $($found.Count)"
}
$changed = Repair-SelfRoutes
if ($changed -ne 1 -or $script:removed.Count -ne 1 -or $script:removed[0] -ne '192.168.68.60/32') {
  throw 'Unsafe removal: changed non-target route'
}
$script:removed = @()
$script:privateAddresses = @([pscustomobject]@{
  InterfaceIndex = 11; IPAddress = '192.168.68.61'; AddressState='Preferred'; PrefixLength=22
})
if (@(Get-BadSelfRoutes).Count -ne 0) { throw 'Should not change old route when own Wi-Fi IP changes' }
if ((Repair-SelfRoutes) -ne 0 -or $script:removed.Count -ne 0) {
  throw 'Changed stale IP route after DHCP reassignment'
}
Write-Host 'PASS: PowerShell parses and route guard changes own-host AmneziaVPN /32 only'
