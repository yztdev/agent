$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\JDK17' }
$packager = Join-Path $jdk 'bin\jpackage.exe'
$image = Join-Path $root 'dist\agent'
if (-not (Test-Path -LiteralPath (Join-Path $image 'agent.exe'))) { throw 'Build the app image first with scripts/package-client.ps1.' }
if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -or -not (Get-Command light.exe -ErrorAction SilentlyContinue)) { throw 'WiX Toolset 3 is required on PATH to build a Windows installer.' }
& $packager --type exe --name agent --app-version 0.1.0 --app-image $image --dest (Join-Path $root 'dist') --win-per-user-install --win-dir-chooser
if ($LASTEXITCODE -ne 0) { throw 'Installer build failed.' }
Write-Host 'Installer created. After installation, run install-client.ps1 in the installation directory to add agent to user PATH.'
