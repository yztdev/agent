$ErrorActionPreference = 'Stop'
$clientDir = $PSScriptRoot
$exe = Join-Path $clientDir 'agent.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw 'Place this script in the same folder as agent.exe.' }
$currentPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$entries = @($currentPath -split ';' | Where-Object { $_ })
if (-not ($entries | Where-Object { $_.TrimEnd('\') -ieq $clientDir.TrimEnd('\') })) {
    $newPath = if ([string]::IsNullOrWhiteSpace($currentPath)) { $clientDir } else { $currentPath.TrimEnd(';') + ';' + $clientDir }
    [Environment]::SetEnvironmentVariable('Path', $newPath, 'User')
    Write-Host "Added $clientDir to your user PATH."
} else { Write-Host 'Client folder is already on your user PATH.' }
Write-Host 'Open a new PowerShell window and run: agent'
Write-Host 'Keep this entire folder in place; moving it requires running this script again.'
