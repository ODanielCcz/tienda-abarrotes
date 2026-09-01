[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$failures = [System.Collections.Generic.List[string]]::new()

function Add-Failure {
    param([string]$Message)
    $failures.Add($Message)
}

function Require-File {
    param([string]$RelativePath)

    $path = Join-Path $repositoryRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-Failure "Falta el archivo requerido: $RelativePath"
        return $null
    }
    return $path
}

function Assert-PowerShellParses {
    param([string]$Path)

    $tokens = $null
    $parseErrors = $null
    [void][Management.Automation.Language.Parser]::ParseFile(
        $Path,
        [ref]$tokens,
        [ref]$parseErrors
    )
    if ($parseErrors.Count -gt 0) {
        Add-Failure "PowerShell no parsea: $Path :: $($parseErrors.Message -join '; ')"
    }
}

$contractPath = Require-File 'tests\performance\baseline\baseline-contract.json'
$helperPath = Require-File 'scripts\_perf-baseline.ps1'
$runnerPath = Require-File 'scripts\run-perf-baseline.ps1'
$prepareSqlPath = Require-File 'tests\performance\baseline\prepare-postgres.sql'
$preflightSqlPath = Require-File 'tests\performance\baseline\preflight-fixture-state.sql'
$resetSqlPath = Require-File 'tests\performance\baseline\reset-postgres-statements.sql'
$snapshotSqlPath = Require-File 'tests\performance\baseline\postgres-snapshot.sql'
$upPath = Require-File 'scripts\perf-environment-up.ps1'
$readmePath = Require-File 'tests\performance\README.md'

foreach ($scriptPath in @($helperPath, $runnerPath)) {
    if ($null -ne $scriptPath) {
        Assert-PowerShellParses -Path $scriptPath
    }
}

if ($null -ne $contractPath) {
    try {
        $contract = Get-Content -Raw -LiteralPath $contractPath | ConvertFrom-Json
        if ($contract.schemaVersion -ne 1) { Add-Failure 'schemaVersion debe ser 1.' }
        if ($contract.composeProjectName -cne 'tienda-perf') {
            Add-Failure 'composeProjectName debe ser exactamente tienda-perf.'
        }
        if ($contract.artifacts.relativeDirectory -cne 'baseline') {
            Add-Failure 'Los artefactos deben usar el subdirectorio baseline externo.'
        }
        if ($contract.officialBaselineWindow -cne 'postWarm') {
            Add-Failure 'La ventana oficial debe ser postWarm.'
        }
        $criticalMetrics = @($contract.prometheusMetrics.critical)
        $optionalMetrics = @($contract.prometheusMetrics.optional)
        if ($criticalMetrics.Count -lt 4) {
            Add-Failure 'El contrato debe declarar al menos cuatro métricas Prometheus críticas.'
        }
        if (Compare-Object $criticalMetrics ($criticalMetrics | Sort-Object -Unique)) {
            Add-Failure 'Las métricas críticas no pueden repetirse.'
        }
        if (Compare-Object $optionalMetrics ($optionalMetrics | Sort-Object -Unique)) {
            Add-Failure 'Las métricas opcionales no pueden repetirse.'
        }
        if (@(Compare-Object $criticalMetrics $optionalMetrics -IncludeEqual -ExcludeDifferent).Count -gt 0) {
            Add-Failure 'Una métrica no puede ser crítica y opcional a la vez.'
        }
        foreach ($timeout in @(
            $contract.timeouts.httpSeconds,
            $contract.timeouts.loginSeconds,
            $contract.timeouts.dockerSeconds,
            $contract.timeouts.psqlSeconds,
            $contract.timeouts.backendTestSeconds,
            $contract.timeouts.webTestSeconds,
            $contract.timeouts.webBuildSeconds
        )) {
            if ([int]$timeout -le 0) { Add-Failure 'Todos los timeouts deben ser positivos.' }
        }
        if ([int]$contract.timing.maximumJitterMilliseconds -le 0 -or
            [int]$contract.timing.maximumWindowOverrunSeconds -le 0) {
            Add-Failure 'El contrato debe definir tolerancias temporales positivas.'
        }
        if ($contract.database.name -cne 'tienda_performance' -or
            $contract.database.owner -cne 'tienda_owner') {
            Add-Failure 'La identidad PostgreSQL debe ser tienda_performance/tienda_owner.'
        }
        foreach ($initialCount in @(
            $contract.requiredInitialState.salesOrders,
            $contract.requiredInitialState.payments,
            $contract.requiredInitialState.syncOperations,
            $contract.requiredInitialState.carts
        )) {
            if ([long]$initialCount -ne 0) {
                Add-Failure 'El contrato oficial debe partir de cero operaciones de negocio.'
            }
        }
        foreach ($windowName in @('preWarm', 'postWarm')) {
            $window = $contract.sampling.$windowName
            if ($window.durationSeconds -ne 300) {
                Add-Failure "$windowName debe durar 300 segundos."
            }
            if ($window.intervalSeconds -ne 5) {
                Add-Failure "$windowName debe muestrear cada 5 segundos."
            }
            if ($window.expectedSamples -ne 61) {
                Add-Failure "$windowName debe declarar 61 muestras inclusivas."
            }
        }
        if ($contract.warmup.requestsPerEndpoint -ne 20) {
            Add-Failure 'El calentamiento debe ejecutar 20 peticiones por endpoint.'
        }
        $endpoints = @($contract.warmup.endpoints)
        if ($endpoints.Count -lt 5) {
            Add-Failure 'Deben declararse al menos cinco endpoints críticos.'
        }
        if (@($endpoints.name | Sort-Object -Unique).Count -ne $endpoints.Count) {
            Add-Failure 'Los nombres de endpoints críticos deben ser únicos.'
        }
        foreach ($endpoint in $endpoints) {
            if ($endpoint.method -cne 'GET' -or $endpoint.path -notmatch '^/') {
                Add-Failure "Endpoint de calentamiento inválido: $($endpoint.name)."
            }
        }
        if ($contract.ports.backend -ne 58081 -or
            $contract.ports.database -ne 55434 -or
            $contract.ports.web -ne 54201) {
            Add-Failure 'Los puertos del baseline no coinciden con el entorno aislado.'
        }
    } catch {
        Add-Failure "baseline-contract.json no es JSON válido: $($_.Exception.Message)"
    }
}

if ($null -ne $helperPath) {
    $helper = Get-Content -Raw -LiteralPath $helperPath
    foreach ($requiredFunction in @(
        'Assert-PerfBaselinePreflight',
        'Assert-PerfKnownFixtures',
        'Assert-PerfInitialDatabaseState',
        'Assert-PerfLoopbackBinding',
        'Assert-NoPerfK6Running',
        'Invoke-PerfNativeProcess',
        'Assert-PerfMetricSample',
        'Assert-PerfServiceInspectionSnapshot',
        'Assert-PerfServiceContainersLive',
        'Initialize-PerfPostgresObservability',
        'Invoke-PerfMetricWindow',
        'Get-PerfPostWarmMedian',
        'New-PerfBaselineSummary',
        'Protect-PerfArtifacts',
        'Protect-PerfFailedRunArtifacts',
        'Get-PerfPathInspection',
        'Assert-PerfPathComponentsSafe',
        'Assert-PerfRunMarker',
        'Assert-PerfBaselineRunDirectory',
        'Remove-PerfArtifactDirectorySafely',
        'Move-PerfArtifactDirectoryToQuarantine',
        'Enter-PerfBaselineLock',
        'Get-PerfScheduledSampleTime'
    )) {
        if ($helper -notmatch "function\s+$requiredFunction\b") {
            Add-Failure "Falta la función segura $requiredFunction."
        }
    }
    if ($helper -match '(?i)write-(host|output).*accessToken|write-(host|output).*password') {
        Add-Failure 'El helper no debe imprimir tokens ni contraseñas.'
    }
    if ($helper -notmatch "Get-PerfCommandVersion\s+-Command\s+'npm\.cmd'") {
        Add-Failure 'La metadata debe resolver npm.cmd explícitamente en Windows.'
    }
    foreach ($requiredSafetyText in @(
        'TIENDA_PERF_BASELINE_RUN_V1',
        '.baseline-run-marker.json',
        'ReparsePoint',
        'yyyyMMddTHHmmssfffZ'
    )) {
        if ($helper -notmatch [regex]::Escape($requiredSafetyText)) {
            Add-Failure "El helper canónico no contiene: $requiredSafetyText"
        }
    }
}

if ($null -ne $preflightSqlPath) {
    $preflightSql = Get-Content -Raw -LiteralPath $preflightSqlPath
    foreach ($source in @(
        'current_database()',
        'current_user',
        'inventory.stock_balances',
        'sales.sales_orders',
        'sales.payments',
        'sync.inbox_operations',
        'sales.carts'
    )) {
        if ($preflightSql -notmatch [regex]::Escape($source)) {
            Add-Failure "preflight-fixture-state.sql debe comprobar $source."
        }
    }
    if ($preflightSql -match '(?i)\b(DROP|TRUNCATE|DELETE|UPDATE|INSERT)\b') {
        Add-Failure 'El preflight de fixtures debe ser estrictamente de solo lectura.'
    }
}

if ($null -ne $runnerPath) {
    $runner = Get-Content -Raw -LiteralPath $runnerPath
    foreach ($requiredText in @(
        'Assert-PerfBaselinePreflight',
        'Invoke-PerfMetricWindow',
        'Invoke-PerfWarmup',
        'Get-PerfPostWarmMedian',
        'New-PerfBaselineSummary',
        'Protect-PerfArtifacts',
        'Protect-PerfFailedRunArtifacts',
        'Enter-PerfBaselineLock',
        'ExpectedRestartCounts',
        'post-warm-statistics-reset',
        'DIAGNOSTIC',
        'baseline-summary.json',
        'baseline-summary.md'
    )) {
        if ($runner -notmatch [regex]::Escape($requiredText)) {
            Add-Failure "El runner no contiene el paso obligatorio: $requiredText"
        }
    }
    if ($runner -match '(?i)write-(host|output).*accessToken|write-(host|output).*password') {
        Add-Failure 'El runner no debe imprimir tokens ni contraseñas.'
    }
}

if ($null -ne $prepareSqlPath) {
    $prepareSql = Get-Content -Raw -LiteralPath $prepareSqlPath
    if ($prepareSql -notmatch '(?i)CREATE\s+EXTENSION\s+IF\s+NOT\s+EXISTS\s+pg_stat_statements') {
        Add-Failure 'prepare-postgres.sql debe crear pg_stat_statements de forma idempotente.'
    }
    if ($prepareSql -match '(?i)\b(DROP|TRUNCATE|DELETE)\b') {
        Add-Failure 'prepare-postgres.sql no puede eliminar datos.'
    }
}

if ($null -ne $snapshotSqlPath) {
    $snapshotSql = Get-Content -Raw -LiteralPath $snapshotSqlPath
    foreach ($source in @('pg_stat_database', 'pg_stat_activity', 'pg_locks', 'pg_stat_statements')) {
        if ($snapshotSql -notmatch [regex]::Escape($source)) {
            Add-Failure "postgres-snapshot.sql debe capturar $source."
        }
    }
    if ($snapshotSql -match '(?i)\b(DROP|TRUNCATE|DELETE|UPDATE|INSERT)\b') {
        Add-Failure 'postgres-snapshot.sql debe ser estrictamente de solo lectura.'
    }
}

if ($null -ne $resetSqlPath) {
    $resetSql = Get-Content -Raw -LiteralPath $resetSqlPath
    if ($resetSql -notmatch '(?i)SELECT\s+pg_stat_statements_reset\s*\(\s*\)') {
        Add-Failure 'reset-postgres-statements.sql debe reiniciar solo las estadísticas de consultas.'
    }
    if ($resetSql -match '(?i)\b(DROP|TRUNCATE|DELETE|UPDATE|INSERT)\b') {
        Add-Failure 'El reset estadístico no puede modificar datos de negocio.'
    }
}

if ($null -ne $upPath) {
    $up = Get-Content -Raw -LiteralPath $upPath
    if ($up -notmatch 'Initialize-PerfPostgresObservability') {
        Add-Failure 'perf-environment-up.ps1 debe inicializar la extensión de observabilidad.'
    }
}

if ($null -ne $readmePath) {
    $readme = Get-Content -Raw -LiteralPath $readmePath
    foreach ($requiredText in @(
        'run-perf-baseline.ps1',
        'postWarm',
        'fuera de OneDrive',
        'DIAGNOSTIC',
        'stock `10000`',
        'timeouts explícitos',
        'lock exclusivo',
        'contador de reinicios',
        'falla cerrada',
        'yyyyMMddTHHmmssfffZ',
        'symlink, junction o reparse',
        'SO, CPU, RAM'
    )) {
        if ($readme -notmatch [regex]::Escape($requiredText)) {
            Add-Failure "README no documenta: $requiredText"
        }
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    throw "Validación estructural del baseline falló con $($failures.Count) problema(s)."
}

Write-Host 'PASS: contrato, scripts, SQL y documentación del baseline son coherentes.' -ForegroundColor Green
