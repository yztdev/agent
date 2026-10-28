$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
mvn -q package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\JDK17' }
$packager = Join-Path $jdk 'bin\jpackage.exe'
if (-not (Test-Path -LiteralPath $packager)) { throw 'Set JAVA_HOME to a JDK containing jpackage.' }
$output = Join-Path $root 'dist'
New-Item -ItemType Directory -Path $output -Force | Out-Null
if (Test-Path -LiteralPath (Join-Path $output 'agent')) { throw 'dist/agent already exists. Move or remove it before packaging.' }
& $packager --type app-image --name agent --input (Join-Path $root 'client\target') --main-jar 'client-0.1.0.jar' --main-class dev.terminalagent.client.Main --dest $output --win-console --add-modules java.net.http,jdk.crypto.ec,java.logging,java.desktop
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'install-client.ps1') -Destination (Join-Path $output 'agent\install-client.ps1')
Write-Host "Created $output\agent\agent.exe"
