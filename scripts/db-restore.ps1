param(
    [Parameter(Mandatory = $true)]
    [string]$BackupPath
)

. (Join-Path $PSScriptRoot '_db-common.ps1')
$resolved = (Resolve-Path -LiteralPath $BackupPath).Path
$confirmation = Read-Host "La restauración reemplazará objetos en $DatabaseName. Escribe RESTAURAR"
if ($confirmation -cne 'RESTAURAR') {
    Write-Host 'Operación cancelada.'
    exit 0
}

$containerPath = '/tmp/tienda-restore.backup'
Invoke-Compose -ComposeArguments @('cp', $resolved, "database:$containerPath")
Invoke-Compose -ComposeArguments @('exec', '-T', 'database', 'pg_restore', '-U', $DatabaseUser, '-d', $DatabaseName, '--clean', '--if-exists', '--no-owner', $containerPath)
Invoke-Compose -ComposeArguments @('exec', '-T', 'database', 'rm', '-f', $containerPath)
Write-Host 'Restauración finalizada.'

