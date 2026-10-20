# KADR AmneziaVPN route recovery (Windows 10/11, PowerShell 5.1+).
# Opt-in. Does not change KillSwitch, firewall rules, VPN settings, or default route.
# Repairs only AmneziaVPN /32 routes incorrectly assigned to this PC's own
# active physical private IPv4 address.
[CmdletBinding()]
param(
  [switch]$Install,
  [switch]$Uninstall,
  [switch]$Watch,
  [switch]$Once,
  [switch]$Status
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$TaskName = 'KADR Amnezia LAN Route Guard'
$InstallDirectory = Join-Path $env:ProgramFiles 'KADR Route Guard'
$InstalledScript = Join-Path $InstallDirectory 'KADR-Amnezia-RouteGuard.ps1'
$LogDirectory = Join-Path $env:ProgramData 'KADR Route Guard'
$LogPath = Join-Path $LogDirectory 'route-guard.log'
$PowerShell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'

function Write-GuardLog([string]$Message) {
  try {
    if (-not (Test-Path -LiteralPath $LogDirectory)) {
      New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
    }
    $line = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -LiteralPath $LogPath -Value $line -Encoding UTF8
  } catch {
    # Logging must never stop network repair.
  }
}

function Assert-Administrator {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
  $principal = New-Object Security.Principal.WindowsPrincipal($identity)
  if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Запустите PowerShell от имени администратора и повторите команду.'
  }
}

function Get-PhysicalPrivateIPv4 {
  $physical = @(Get-NetAdapter -Physical -ErrorAction Stop |
    Where-Object { $_.Status -eq 'Up' } |
    Select-Object -ExpandProperty ifIndex)
  if ($physical.Count -eq 0) { return @() }
  $addresses = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
    Where-Object {
      $physical -contains $_.InterfaceIndex -and
      $_.AddressState -eq 'Preferred' -and
      $_.PrefixLength -le 30 -and
      $_.IPAddress -match '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)'
    } |
    Select-Object -ExpandProperty IPAddress -Unique)
  return $addresses
}

function Get-BadSelfRoutes {
  $addresses = @(Get-PhysicalPrivateIPv4)
  if ($addresses.Count -eq 0) { return @() }
  $vpnAdapters = @(Get-NetAdapter -ErrorAction Stop |
    Where-Object { $_.Name -match '^AmneziaVPN($|\s)' } |
    Select-Object -ExpandProperty ifIndex)
  if ($vpnAdapters.Count -eq 0) { return @() }
  return @(Get-NetRoute -AddressFamily IPv4 -ErrorAction Stop |
    Where-Object {
      $vpnAdapters -contains $_.InterfaceIndex -and
      $_.NextHop -eq '0.0.0.0' -and
      $_.DestinationPrefix -match '^(.+)/32$' -and
      $addresses -contains ($_.DestinationPrefix -replace '/32$', '')
    })
}

function Repair-SelfRoutes {
  $badRoutes = @(Get-BadSelfRoutes)
  foreach ($route in $badRoutes) {
    $name = $route.DestinationPrefix
    $ifIndex = $route.InterfaceIndex
    try {
      # Exact /32 route on Amnezia's interface only. Never touch default routes,
      # remote LAN hosts, any non-Amnezia interface, or VPN firewall/KillSwitch.
      $route | Remove-NetRoute -Confirm:$false -ErrorAction Stop
      Write-GuardLog "Removed incorrect self-route $name from AmneziaVPN interface $ifIndex"
    } catch {
      Write-GuardLog "Could not remove $name (interface $ifIndex): $($_.Exception.Message)"
    }
  }
  return $badRoutes.Count
}

if ($Install) {
  Assert-Administrator
  New-Item -ItemType Directory -Path $InstallDirectory -Force | Out-Null
  Copy-Item -LiteralPath $PSCommandPath -Destination $InstalledScript -Force
  $arg = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "{0}" -Watch' -f $InstalledScript
  $action = New-ScheduledTaskAction -Execute $PowerShell -Argument $arg
  $triggers = @(
    New-ScheduledTaskTrigger -AtStartup
    New-ScheduledTaskTrigger -AtLogOn
  )
  $settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew
  $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
  Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $triggers -Settings $settings -Principal $principal -Force | Out-Null
  Start-ScheduledTask -TaskName $TaskName
  Write-Host 'KADR Route Guard установлен: маршруты будут восстанавливаться автоматически.'
  Write-Host 'KillSwitch не изменён. При активном KillSwitch локальная сеть может оставаться заблокированной.'
  exit 0
}

if ($Uninstall) {
  Assert-Administrator
  $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
  if ($null -ne $task) {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
  }
  Remove-Item -LiteralPath $InstallDirectory -Recurse -Force -ErrorAction SilentlyContinue
  Write-Host 'KADR Route Guard удалён. Настройки AmneziaVPN не изменены.'
  exit 0
}

if ($Status) {
  $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
  if ($null -eq $task) { Write-Host 'Route Guard: не установлен' }
  else { Write-Host ("Route Guard: {0}" -f $task.State) }
  $badRoutes = @(Get-BadSelfRoutes)
  Write-Host ("Неправильных маршрутов к собственному IP: {0}" -f $badRoutes.Count)
  foreach ($route in $badRoutes) { Write-Host ("{0} via AmneziaVPN" -f $route.DestinationPrefix) }
  exit 0
}

if ($Once) {
  Assert-Administrator
  $count = Repair-SelfRoutes
  Write-Host ("Удалено неправильных маршрутов: {0}" -f $count)
  exit 0
}

if ($Watch) {
  Assert-Administrator
  Write-GuardLog 'Route guard started'
  while ($true) {
    try { [void](Repair-SelfRoutes) }
    catch { Write-GuardLog ("Recovery check failed: " + $_.Exception.Message) }
    Start-Sleep -Seconds 5
  }
}

Write-Host 'Usage: .\KADR-Amnezia-RouteGuard.ps1 -Install | -Status | -Once | -Uninstall'
