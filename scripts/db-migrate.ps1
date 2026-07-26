. (Join-Path $PSScriptRoot '_db-common.ps1')
Invoke-Compose -ComposeArguments @('--profile', 'migrate', 'run', '--rm', 'flyway', 'migrate')
Invoke-Compose -ComposeArguments @('--profile', 'migrate', 'run', '--rm', 'flyway', 'validate')

