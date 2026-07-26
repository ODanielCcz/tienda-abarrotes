. (Join-Path $PSScriptRoot '_db-common.ps1')
Invoke-Compose -ComposeArguments @('--profile', 'tools', 'up', '-d', 'pgadmin')
Write-Host 'pgAdmin disponible en el puerto configurado por PGADMIN_PORT.'

