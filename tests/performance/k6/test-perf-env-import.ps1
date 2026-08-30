[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$temporaryEnvFile = Join-Path ([IO.Path]::GetTempPath()) "tienda-perf-import-$PID.env"
$trackedNames = @('PERF_USERNAME', 'PERF_FIXTURE_USER_PASSWORD', 'PERF_PASSWORD', 'E2E_PASSWORD', 'BOOTSTRAP_ADMIN_PASSWORD')
$previousValues = @{}

try {
    foreach ($name in $trackedNames) {
        $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }

    . (Join-Path $repositoryRoot 'scripts\_perf-common.ps1')
    $PerfEnvFile = $temporaryEnvFile

    foreach ($name in @('PERF_USERNAME', 'PERF_FIXTURE_USER_PASSWORD', 'PERF_PASSWORD')) {
        [Environment]::SetEnvironmentVariable($name, "inherited-$name", 'Process')
    }
    [IO.File]::WriteAllText($temporaryEnvFile, "E2E_PASSWORD=privileged`n")

    $rejected = $false
    try {
        Import-PerfEnvironment
    } catch {
        $rejected = $_.Exception.Message -match 'debe estar presente'
    }
    if (-not $rejected) {
        throw 'El modo normal aceptó claves PERF_* heredadas que no estaban en perf.env.'
    }

    Import-PerfEnvironment -TeardownOnly
    if ($env:PERF_USERNAME -notmatch '^teardown-' -or $env:PERF_PASSWORD -cne $env:PERF_FIXTURE_USER_PASSWORD) {
        throw 'TeardownOnly no reemplazó las credenciales por placeholders seguros.'
    }

    [IO.File]::WriteAllText($temporaryEnvFile, @"
E2E_PASSWORD=privileged
PERF_USERNAME=perf_operator
PERF_FIXTURE_USER_PASSWORD=privileged
PERF_PASSWORD=privileged
"@)
    $reusedPrivilegedCredentialRejected = $false
    try {
        Import-PerfEnvironment
    } catch {
        $reusedPrivilegedCredentialRejected = $_.Exception.Message -match 'no pueden reutilizar'
    }
    if (-not $reusedPrivilegedCredentialRejected) {
        throw 'El modo normal aceptó una contraseña E2E privilegiada para perf_operator.'
    }

    [IO.File]::WriteAllText($temporaryEnvFile, @"
E2E_PASSWORD=privileged
PERF_USERNAME=perf_operator
PERF_FIXTURE_USER_PASSWORD=operator-only
PERF_PASSWORD=operator-only
"@)
    Import-PerfEnvironment
    if (@($script:PerfEnvironmentLoadedNames | Where-Object { $_ -in @('PERF_USERNAME', 'PERF_FIXTURE_USER_PASSWORD', 'PERF_PASSWORD') }).Count -ne 3) {
        throw 'No se registraron las tres claves PERF_* cargadas desde perf.env.'
    }

    Write-Host 'perf.env provenance paths passed.'
} finally {
    foreach ($name in $trackedNames) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process')
    }
    if (Test-Path -LiteralPath $temporaryEnvFile) {
        Remove-Item -LiteralPath $temporaryEnvFile -Force
    }
}
