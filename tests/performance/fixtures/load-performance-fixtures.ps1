[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$contractPath = Join-Path $PSScriptRoot 'fixture-contract.json'
$fixtureSqlPath = Join-Path $PSScriptRoot 'performance-fixtures.sql'
$commonScript = Join-Path $repositoryRoot 'scripts\_perf-common.ps1'

. $commonScript

Assert-PerfDockerAvailable
Import-PerfEnvironment

$contract = Get-Content -Raw -LiteralPath $contractPath | ConvertFrom-Json
$fixturePassword = $env:PERF_FIXTURE_USER_PASSWORD
if ([string]::IsNullOrWhiteSpace($fixturePassword)) {
    throw 'Define PERF_FIXTURE_USER_PASSWORD en perf.env antes de cargar fixtures de rendimiento.'
}

$databaseContainerId = Get-PerfComposeOutput -Arguments @('ps', '-q', 'database')
if ([string]::IsNullOrWhiteSpace($databaseContainerId)) {
    throw 'No se encontró el contenedor PostgreSQL del proyecto tienda-perf.'
}

$previousFixturePassword = $env:PERF_FIXTURE_USER_PASSWORD
$previousPgPassword = $env:PGPASSWORD

try {
    $env:PERF_FIXTURE_USER_PASSWORD = $fixturePassword
    $env:PGPASSWORD = $env:POSTGRES_PASSWORD

    $psqlVariables = @(
        "max_virtual_users=$([int]$contract.maxVirtualUsers)",
        "non_lot_presentations=$([int]$contract.counts.nonLotPresentations)",
        "lot_presentations=$([int]$contract.counts.lotPresentations)",
        "lots_per_lot_presentation=$([int]$contract.counts.lotsPerLotPresentation)",
        "customer_count=$([int]$contract.counts.customers)",
        "supplier_count=$([int]$contract.counts.suppliers)",
        "mobile_device_count=$([int]$contract.counts.mobileDevices)",
        "cash_register_count=$([int]$contract.counts.cashRegisters)"
    )

    $dockerArguments = @(
        'exec', '-i',
        '-e', 'PGPASSWORD',
        '-e', 'PERF_FIXTURE_USER_PASSWORD',
        $databaseContainerId,
        'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1'
    )
    foreach ($variable in $psqlVariables) {
        $dockerArguments += @('-v', $variable)
    }
    $dockerArguments += @(
        '-U', $env:POSTGRES_USER,
        '-d', $env:POSTGRES_DB
    )

    Get-Content -Raw -LiteralPath $fixtureSqlPath | & docker @dockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "La carga de fixtures terminó con código $LASTEXITCODE."
    }
}
finally {
    $env:PERF_FIXTURE_USER_PASSWORD = $previousFixturePassword
    $env:PGPASSWORD = $previousPgPassword
}

Write-Host (
    "Fixtures '$($contract.dataset)' listos para $($contract.maxVirtualUsers) VU " +
    "en la sucursal $($contract.branch.code). Usuario: $($contract.restrictedUser.username)."
) -ForegroundColor Green
