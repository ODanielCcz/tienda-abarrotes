. (Join-Path $PSScriptRoot '_db-common.ps1')
Invoke-Compose -ComposeArguments @('exec', '-T', 'database', 'psql', '-U', $DatabaseUser, '-d', $DatabaseName, '-c', 'TABLE audit.unregistered_database_roles;')
Invoke-Compose -ComposeArguments @('exec', '-T', 'database', 'sh', '-c', "grep -h 'AUDIT:' /var/log/postgresql/*.json 2>/dev/null | tail -n 50 || true")

