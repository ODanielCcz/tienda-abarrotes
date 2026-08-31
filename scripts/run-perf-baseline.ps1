[CmdletBinding()]
param(
    [switch]$ValidateOnly,
    [switch]$SkipProjectVerification
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_perf-common.ps1')
. (Join-Path $PSScriptRoot '_perf-baseline.ps1')

$contract = Get-PerfBaselineContract
& (Join-Path $PerfProjectRoot 'tests\performance\baseline\validate-baseline.ps1')

$campaignLock = $null
$artifactDirectory = $null
$manifestPath = $null
$manifest = $null
$bearerToken = $null
$stage = 'campaign-lock'

try {
    $campaignLock = Enter-PerfBaselineLock

    $stage = 'preflight'
    $preflight = Assert-PerfBaselinePreflight -Contract $contract
    Initialize-PerfPostgresObservability
    if ($ValidateOnly) {
        Write-Output 'Preflight correcto: tienda-perf está limpio, aislado, saludable y listo para una captura.'
        return
    }

    $artifactDirectory = New-PerfBaselineArtifactDirectory -Contract $contract
    $manifestPath = Join-Path $artifactDirectory 'run-manifest.json'
    $startedAt = [DateTimeOffset]::UtcNow
    $manifest = [ordered]@{
        schemaVersion = 1
        status = 'RUNNING'
        official = $false
        startedAt = $startedAt.ToString('o')
        completedAt = $null
        composeProjectName = $PerfProjectName
        dataset = $contract.dataset
        projectVerificationSkipped = [bool]$SkipProjectVerification
        artifactDirectory = $artifactDirectory
        failedStage = $null
    }
    $manifest | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM

    $stage = 'environment-metadata'
    $environment = Get-PerfEnvironmentMetadata -ServiceContainers $preflight.serviceContainers
    if ($environment.gitDirtyEntryCount -gt 0) {
        throw 'La captura requiere un árbol Git limpio para identificar exactamente la revisión medida.'
    }
    $environment | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $artifactDirectory 'environment.json') -Encoding utf8NoBOM

    $stage = 'project-verification'
    if (-not $SkipProjectVerification) {
        Invoke-PerfProjectVerification -ArtifactDirectory $artifactDirectory -Contract $contract
    }

    $stage = 'angular-build-manifest'
    $angularBuild = Get-PerfAngularBuildManifest
    $angularBuild | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $artifactDirectory 'angular-build.json') -Encoding utf8NoBOM

    $stage = 'restricted-authentication'
    $bearerToken = Get-PerfBearerToken -Contract $contract

    $stage = 'pre-warm-statistics-reset'
    Reset-PerfPostgresStatements
    $stage = 'pre-warm-window'
    $preWarm = Invoke-PerfMetricWindow `
        -Name 'preWarm' -Window $contract.sampling.preWarm -Contract $contract `
        -BearerToken $bearerToken -ServiceContainers $preflight.serviceContainers `
        -ExpectedRestartCounts $preflight.serviceRestartCounts `
        -ArtifactDirectory $artifactDirectory

    $stage = 'serial-warmup'
    $warmup = Invoke-PerfWarmup -Contract $contract `
        -FixtureContract $preflight.fixtureContract -BearerToken $bearerToken
    $warmup | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $artifactDirectory 'warmup-requests.json') -Encoding utf8NoBOM

    # La ventana oficial mide solo postWarm, no preWarm ni el calentamiento serial.
    $stage = 'post-warm-statistics-reset'
    Reset-PerfPostgresStatements
    $stage = 'post-warm-window'
    $postWarm = Invoke-PerfMetricWindow `
        -Name 'postWarm' -Window $contract.sampling.postWarm -Contract $contract `
        -BearerToken $bearerToken -ServiceContainers $preflight.serviceContainers `
        -ExpectedRestartCounts $preflight.serviceRestartCounts `
        -ArtifactDirectory $artifactDirectory

    $stage = 'summary'
    $warmupSummary = @($warmup | Group-Object endpoint | ForEach-Object {
        $durations = [double[]]@($_.Group.durationMs)
        $sorted = @($durations | Sort-Object)
        $p95Index = [Math]::Min($sorted.Count - 1, [Math]::Ceiling($sorted.Count * 0.95) - 1)
        [pscustomobject]@{
            endpoint = $_.Name
            requestCount = $_.Count
            medianMs = Get-PerfMedian -Values $durations
            p95Ms = [double]$sorted[$p95Index]
            maximumMs = [double]$sorted[-1]
        }
    })
    $postWarmStatistics = Get-PerfPostWarmMedian -Samples $postWarm -Contract $contract
    $preWarmTiming = Get-Content -Raw -LiteralPath (
        Join-Path $artifactDirectory 'preWarm\window-timing.json'
    ) | ConvertFrom-Json
    $postWarmTiming = Get-Content -Raw -LiteralPath (
        Join-Path $artifactDirectory 'postWarm\window-timing.json'
    ) | ConvertFrom-Json
    $completedAt = [DateTimeOffset]::UtcNow
    $summary = New-PerfBaselineSummary `
        -SkipProjectVerification:$SkipProjectVerification `
        -StartedAt $startedAt -CompletedAt $completedAt -Contract $contract `
        -Environment $environment -AngularBuild $angularBuild `
        -PreWarmSampleCount $preWarm.Count -WarmupSummary $warmupSummary `
        -PostWarmStatistics $postWarmStatistics `
        -PreWarmTiming $preWarmTiming -PostWarmTiming $postWarmTiming
    $summary | ConvertTo-Json -Depth 16 |
        Set-Content -LiteralPath (Join-Path $artifactDirectory 'baseline-summary.json') -Encoding utf8NoBOM
    Write-PerfBaselineMarkdown -Summary $summary `
        -Path (Join-Path $artifactDirectory 'baseline-summary.md')

    $stage = 'artifact-sanitization'
    $sanitization = Protect-PerfArtifacts -ArtifactDirectory $artifactDirectory `
        -AdditionalSensitiveValues @($bearerToken)
    $sanitization | ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath (Join-Path $artifactDirectory 'artifact-sanitization.json') `
        -Encoding utf8NoBOM
    [void](Protect-PerfArtifacts -ArtifactDirectory $artifactDirectory `
        -AdditionalSensitiveValues @($bearerToken))

    $manifest.status = $summary.status
    $manifest.official = $summary.official
    $manifest.completedAt = $completedAt.ToString('o')
    $manifest | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
    Write-Output "Captura guardada en $artifactDirectory"
    if ($summary.official) {
        Write-Output 'La mediana oficial corresponde exclusivamente a la ventana postWarm.'
    }
    else {
        Write-Output 'Captura DIAGNOSTIC: al omitir las suites no constituye una baseline oficial.'
    }
}
catch {
    $runFailure = $_
    if ($null -ne $manifest -and $null -ne $manifestPath) {
        $manifest.status = 'FAILED'
        $manifest.official = $false
        $manifest.completedAt = [DateTimeOffset]::UtcNow.ToString('o')
        $manifest.failedStage = $stage
        $manifest | ConvertTo-Json -Depth 8 |
            Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
        try {
            [void](Protect-PerfFailedRunArtifacts `
                -ArtifactDirectory $artifactDirectory `
                -AdditionalSensitiveValues @($bearerToken))
        }
        catch {
            throw "La etapa '$stage' falló y la evidencia no pudo sanearse; se aplicó eliminación fail-closed."
        }
    }
    throw $runFailure
}
finally {
    $bearerToken = $null
    if ($null -ne $campaignLock) { $campaignLock.Dispose() }
}
