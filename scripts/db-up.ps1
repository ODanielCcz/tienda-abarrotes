. (Join-Path $PSScriptRoot '_db-common.ps1')
Invoke-Compose -ComposeArguments @('up', '--build', '-d', 'database')
Invoke-Compose -ComposeArguments @('ps', 'database')

