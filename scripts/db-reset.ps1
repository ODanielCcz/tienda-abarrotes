. (Join-Path $PSScriptRoot '_db-common.ps1')
$confirmation = Read-Host 'Esta operación eliminará los volúmenes locales de PostgreSQL y pgAdmin. Escribe ELIMINAR'
if ($confirmation -cne 'ELIMINAR') {
    Write-Host 'Operación cancelada.'
    exit 0
}
Invoke-Compose -ComposeArguments @('down', '--volumes', '--remove-orphans')
Write-Host 'Entorno local eliminado. Ejecuta db-up.ps1 para reconstruirlo.'

