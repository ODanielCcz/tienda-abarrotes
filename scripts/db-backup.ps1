. (Join-Path $PSScriptRoot '_db-common.ps1')
$backupDir = Join-Path $ProjectRoot 'data\local-only\backups'
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$output = Join-Path $backupDir "tienda-abarrotes-$timestamp.backup"
$containerPath = "/tmp/tienda-abarrotes-$timestamp.backup"
Invoke-Compose -ComposeArguments @('exec', '-T', 'database', 'pg_dump', '-U', $DatabaseUser, '-d', $DatabaseName, '-Fc', '-f', $containerPath)
Invoke-Compose -ComposeArguments @('cp', "database:$containerPath", $output)
Invoke-Compose -ComposeArguments @('exec', '-T', 'database', 'rm', '-f', $containerPath)
Write-Host "Respaldo creado en $output"

