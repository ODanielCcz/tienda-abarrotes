[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$repositoryRoot = (Resolve-Path (Join-Path $root '..\..')).Path
$requiredFiles = @(
    'k6/backend-smoke.js',
    'k6/test-perf-env-import.ps1',
    'k6/lib/config.js',
    'k6/lib/auth.js',
    'k6/lib/checks.js',
    'k6/lib/fixtures.js'
)
$requiredTokens = @{
    'k6/backend-smoke.js' = @(
        'setup()', 'EXPECT_INVALID_URL', 'CART_UPSERT', "data.status === 'ACCEPTED'",
        'SMOKE_RUN_ID', 'createSalesOrder', 'createCardPayment', 'acknowledgeCheckpoint', 'typeof __ITER', '[200, 404]', 'failedCheckNames', 'data.id === fixture.productPresentationId', 'deterministic write IDs are valid and distinct', 'smoke_completion', 'expectAbortMidway', 'payment replay resolves to the original payment', 'sync replay resolves to the original operation', 'stock decrement is bounded by one sale'
    )
    'k6/lib/config.js' = @('fixture-contract.json', 'SMOKE_RUN_ID', 'BASE_URL', 'EXPECT_ABORT_MIDWAY')
    'k6/lib/auth.js' = @('loginInSetup', 'renewTokenInSetup', 'accessToken', "config.username !== 'perf_operator'")
    'k6/lib/checks.js' = @(
        'unexpected_5xx', 'unexpected_functional_error', 'schema_error',
        'read_latency_ms', 'write_latency_ms', 'X-Correlation-ID', 'headerValue', 'timestamp', 'message', 'hasOwnProperty'
    )
    'k6/lib/fixtures.js' = @('nonLotPresentationId', 'nonLotUnitPrice', 'deviceId')
}

$requiredPatterns = @{
    'k6/backend-smoke.js' = @(
        @{ Pattern = 'smokeCompletion\.add\(0\)'; Minimum = 1 },
        @{ Pattern = 'smokeCompletion\.add\(1\)'; Minimum = 1 },
        @{ Pattern = 'paymentReplayId\s*===\s*paymentId'; Minimum = 1 },
        @{ Pattern = 'replaySyncOperationId\s*===\s*syncOperationId'; Minimum = 1 },
        @{ Pattern = 'stockBefore\s*===\s*fixture\.initialAvailableQuantity'; Minimum = 1 },
        @{ Pattern = 'stockAfterAvailable\s*===\s*fixture\.initialAvailableQuantity\s*-\s*1'; Minimum = 1 }
    )
    'k6/lib/checks.js' = @(
        @{ Pattern = "Object\.prototype\.hasOwnProperty\.call\(envelope, 'data'\)"; Minimum = 1 },
        @{ Pattern = "Object\.prototype\.hasOwnProperty\.call\(envelope, 'errors'\)"; Minimum = 1 }
    )
}

$failures = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $requiredFiles) {
    $path = Join-Path $root $relativePath
    if (-not (Test-Path -LiteralPath $path)) {
        $failures.Add("Falta $relativePath")
        continue
    }

    $content = Get-Content -Raw -LiteralPath $path
    foreach ($token in $requiredTokens[$relativePath]) {
        if ($content -notlike "*$token*") {
            $failures.Add("$relativePath no contiene '$token'")
        }
    }
    foreach ($requirement in $requiredPatterns[$relativePath]) {
        $matches = [regex]::Matches($content, $requirement.Pattern).Count
        if ($matches -lt $requirement.Minimum) {
            $failures.Add("$relativePath requiere al menos $($requirement.Minimum) coincidencia(s) de '$($requirement.Pattern)'")
        }
    }
}

$fixtureContract = Get-Content -Raw -LiteralPath (Join-Path $root 'fixtures\fixture-contract.json') | ConvertFrom-Json
if ($fixtureContract.warehouses[0].initialAvailableQuantity -ne 10000) {
    $failures.Add('fixture-contract.json debe declarar initialAvailableQuantity=10000 para el almacén primario.')
}

$perfCommon = Get-Content -Raw -LiteralPath (Join-Path $repositoryRoot 'scripts\_perf-common.ps1')
foreach ($pattern in @(
    'param\(\[switch\]\$TeardownOnly\)',
    '\$env:PERF_USERNAME\s+-cne\s+''perf_operator''',
    '\$env:PERF_PASSWORD\s+-cne\s+\$env:PERF_FIXTURE_USER_PASSWORD',
    '\$env:PERF_FIXTURE_USER_PASSWORD\s+-ceq\s+\$privilegedPassword',
    '\$env:PERF_PASSWORD\s+-ceq\s+\$privilegedPassword'
)) {
    if ([regex]::Matches($perfCommon, $pattern).Count -lt 1) {
        $failures.Add("scripts/_perf-common.ps1 no satisface la regla '$pattern'.")
    }
}

$fixtureLoader = Get-Content -Raw -LiteralPath (Join-Path $root 'fixtures\load-performance-fixtures.ps1')
if ($fixtureLoader -match '\$env:E2E_PASSWORD') {
    $failures.Add('El loader de fixtures no debe usar E2E_PASSWORD como fallback.')
}

foreach ($pattern in @(
    '\$loadedNames\.Add\(',
    '\$loadedNames\.Contains\(',
    'param\(\[switch\]\$TeardownOnly\)'
)) {
    if ([regex]::Matches($perfCommon, $pattern).Count -lt 1) {
        $failures.Add("scripts/_perf-common.ps1 no controla procedencia de perf.env: '$pattern'.")
    }
}

$environmentUp = Get-Content -Raw -LiteralPath (Join-Path $repositoryRoot 'scripts\perf-environment-up.ps1')
foreach ($pattern in @(
    'Import-PerfEnvironment',
    'scripts\\perf-environment-down\.ps1',
    'scripts\\perf-environment-up\.ps1'
)) {
    if ([regex]::Matches($environmentUp, $pattern).Count -lt 1) {
        $failures.Add("scripts/perf-environment-up.ps1 no contiene la guía fail-closed esperada: '$pattern'.")
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

& (Join-Path $PSScriptRoot 'test-perf-env-import.ps1')
if ($LASTEXITCODE -ne 0) {
    throw 'Falló la prueba de procedencia y separación de credenciales de perf.env.'
}

Write-Host 'Smoke k6 structural validator passed.'
