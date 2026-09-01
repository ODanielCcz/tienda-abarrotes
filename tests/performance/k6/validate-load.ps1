$ErrorActionPreference = 'Stop'

$performanceRoot = Split-Path -Parent $PSScriptRoot
$requiredFiles = @(
    'k6/backend-load.js',
    'k6/lib/profile-config.js',
    'k6/lib/performance-ids.js',
    'k6/lib/workload-metrics.js',
    'k6/lib/workload.js',
    'sql/performance-invariants.sql',
    'scripts/run-perf-load.ps1'
)

$failures = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $requiredFiles) {
    $path = Join-Path $performanceRoot $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $failures.Add("Falta $relativePath")
    }
}

if ($failures.Count -eq 0) {
    $load = Get-Content -LiteralPath (Join-Path $performanceRoot 'k6/backend-load.js') -Raw
    $profile = Get-Content -LiteralPath (Join-Path $performanceRoot 'k6/lib/profile-config.js') -Raw
    $workload = Get-Content -LiteralPath (Join-Path $performanceRoot 'k6/lib/workload.js') -Raw
    $metrics = Get-Content -LiteralPath (Join-Path $performanceRoot 'k6/lib/workload-metrics.js') -Raw
    $checks = Get-Content -LiteralPath (Join-Path $performanceRoot 'k6/lib/checks.js') -Raw
    $runner = Get-Content -LiteralPath (Join-Path $performanceRoot 'scripts/run-perf-load.ps1') -Raw
    $invariants = Get-Content -LiteralPath (Join-Path $performanceRoot 'sql/performance-invariants.sql') -Raw

    foreach ($requiredText in @('stages', 'handleSummary', 'executeWorkloadIteration', 'summaryTrendStats', 'systemTags')) {
        if ($load -notmatch [regex]::Escape($requiredText)) {
            $failures.Add("backend-load.js no contiene $requiredText")
        }
    }
    if ($load -match "systemTags:\s*\[[^\]]*'url'") {
        $failures.Add('backend-load.js no debe habilitar el tag url de alta cardinalidad.')
    }
    if ($checks -notmatch 'name:\s*expected\.name') {
        $failures.Add('checks.js debe agrupar la métrica HTTP con name=expected.name.')
    }
    foreach ($requiredText in @('read_p95', 'write_p95', 'business_p99', 'p(99)<1500', 'unexpected_5xx')) {
        if ($profile -notmatch [regex]::Escape($requiredText)) {
            $failures.Add("profile-config.js no contiene $requiredText")
        }
    }
    foreach ($requiredText in @('executeWorkloadIteration', 'CART_UPSERT', 'sales-order', 'card-payment')) {
        if ($workload -notmatch [regex]::Escape($requiredText)) {
            $failures.Add("workload.js no contiene $requiredText")
        }
    }
    foreach ($requiredText in @('module', 'endpoint', 'operation', 'functional_result')) {
        if ($metrics -notmatch [regex]::Escape($requiredText)) {
            $failures.Add("workload-metrics.js no contiene la etiqueta $requiredText")
        }
    }
    foreach ($requiredText in @('RunCount = 3', 'CooldownSeconds = 300', 'run-perf-k6.ps1', 'validate-performance-invariants.ps1')) {
        if ($runner -notmatch [regex]::Escape($requiredText)) {
            $failures.Add("run-perf-load.ps1 no contiene $requiredText")
        }
    }
    foreach ($requiredText in @('negative_stock', 'payment_overflow', 'orphan_sale_movement', 'sync_sequence_gap')) {
        if ($invariants -notmatch [regex]::Escape($requiredText)) {
            $failures.Add("performance-invariants.sql no comprueba $requiredText")
        }
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host 'Load k6 structural validator passed.'
