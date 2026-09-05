[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$failures = [System.Collections.Generic.List[string]]::new()

function Require-Content {
    param(
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string[]]$Patterns
    )

    $path = Join-Path $repositoryRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $failures.Add("Falta $RelativePath")
        return
    }
    $content = Get-Content -LiteralPath $path -Raw
    foreach ($pattern in $Patterns) {
        if ($content -notmatch $pattern) {
            $failures.Add("$RelativePath no contiene el contrato esperado: $pattern")
        }
    }
}

foreach ($profile in @('stress', 'spike', 'soak')) {
    Require-Content -RelativePath "tests/performance/k6/backend-$profile.js" -Patterns @(
        "${profile}Profile",
        'executeProfile',
        'optionsFor',
        'setupProfile',
        'teardownProfile',
        'handleSummary'
    )
}

Require-Content -RelativePath 'tests/performance/k6/lib/profile-runtime.js' -Patterns @(
    'executeWorkloadIteration',
    'readinessRequest',
    'stableSystemTags',
    'comparableSummary',
    'unexpected_functional_error\?\.count',
    'unexpected_5xx\?\.count'
)
Require-Content -RelativePath 'tests/performance/k6/lib/checks.js' -Patterns @(
    "new Rate\('unexpected_5xx_rate'\)",
    "new Rate\('unexpected_functional_error_rate'\)"
)

Require-Content -RelativePath 'tests/performance/k6/lib/profile-config.js' -Patterns @(
    'stressProfile',
    '(?s)50.*75.*100.*150',
    'unexpected_5xx_rate',
    'unexpected_functional_error_rate',
    'spikeProfile',
    '150',
    'soakProfile',
    '25',
    '28m'
)
Require-Content -RelativePath 'tests/performance/scripts/run-fullstack-stress.ps1' -Patterns @(
    "ValidateSet\('stress', 'spike', 'soak'\)",
    'backend-\$profile\.js',
    'validate-performance-invariants.ps1',
    'readinessRecoverySeconds',
    '\$null -ne \$k6Summary',
    'samples.jsonl',
    'COMPLETED'
)
Require-Content -RelativePath 'tests/performance/scripts/capture-performance-snapshot.ps1' -Patterns @(
    'actuator/prometheus',
    'docker stats',
    'postgres-snapshot.sql',
    'readiness'
)
Require-Content -RelativePath 'tests/performance/README.md' -Patterns @(
    'Stress, spike y soak',
    'run-fullstack-stress.ps1',
    '30 minutos'
)

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "ERROR: $_" -ForegroundColor Red }
    throw "Falló el contrato de stress/spike/soak con $($failures.Count) hallazgo(s)."
}

Write-Output 'PASS: perfiles stress, spike y soak cumplen el contrato estructural.'
