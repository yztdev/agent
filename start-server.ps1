param([switch]$ResetCredentials)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
Set-Location $root
$configDir = Join-Path $env:APPDATA 'terminal-agent-server'
New-Item -ItemType Directory -Path $configDir -Force | Out-Null
function Get-SavedSecret([string]$Name, [string]$Prompt) {
    $path = Join-Path $configDir "$Name.xml"
    if ($ResetCredentials -or -not (Test-Path -LiteralPath $path)) {
        $value = Read-Host $Prompt -AsSecureString
        if ($value.Length -eq 0) { throw "$Name cannot be empty." }
        $value | Export-Clixml -LiteralPath $path
    } else { $value = Import-Clixml -LiteralPath $path }
    if ($value -isnot [System.Security.SecureString]) { throw "Invalid stored credential: $path" }
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($value)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
if (-not $env:DATABASE_URL) { $env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/terminal_agent' }
if (-not $env:DATABASE_USER) { $env:DATABASE_USER = 'agent' }
if (-not $env:DATABASE_PASSWORD) { $env:DATABASE_PASSWORD = Get-SavedSecret 'database-password' 'Database password' }
if (-not $env:TLS_KEYSTORE) { $env:TLS_KEYSTORE = Join-Path $env:USERPROFILE 'terminal-agent-dev.p12' }
if (-not (Test-Path -LiteralPath $env:TLS_KEYSTORE)) { throw "Certificate keystore not found: $env:TLS_KEYSTORE. Set TLS_KEYSTORE to the correct file." }
if (-not $env:TLS_KEYSTORE_PASSWORD) { $env:TLS_KEYSTORE_PASSWORD = Get-SavedSecret 'keystore-password' 'Certificate keystore password' }
$container = docker ps -a --filter 'name=^/agent-postgres-1$' --format '{{.Names}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker is unavailable. Start Docker Desktop.' }
if ($container -eq 'agent-postgres-1') {
    docker start agent-postgres-1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to start the database container.' }
} else { Write-Host 'Project container not found; using the configured database address.' }
$jar = Join-Path $root 'server\target\server-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Server JAR not found. Run mvn package first.' }
try { & java -jar $jar; if ($LASTEXITCODE -ne 0) { throw 'Server stopped with an error. Check the database credentials and certificate.' } }
finally { Remove-Item Env:DATABASE_PASSWORD,Env:TLS_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue }
