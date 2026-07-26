. (Join-Path $PSScriptRoot '_db-common.ps1')
Invoke-Compose -ComposeArguments @('--profile', 'seed', 'run', '--rm', 'db-seed')

