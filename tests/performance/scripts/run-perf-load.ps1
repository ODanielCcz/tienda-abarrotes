[CmdletBinding()]
param(
    [ValidateRange(1, 3)]
    [int]$RunCount = 3,
    [ValidateRange(0, 3600)]
    [int]$CooldownSeconds = 300,
    [switch]$SkipCooldown,
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $repositoryRoot 'scripts\_perf-common.ps1')

$validator = Join-Path $repositoryRoot 'tests\performance\k6\validate-load.ps1'
& $validator
if (-not $?) {
    throw 'Falló el validador estructural de carga.'
}
if ($ValidateOnly) {
    & (Join-Path $repositoryRoot 'scripts\run-perf-k6.ps1') -Command inspect -Script backend-load.js
    return
}

Assert-PerfDockerAvailable
New-PerfEnvironmentFile
Import-PerfEnvironment
$campaignId = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$loadArtifactDirectory = Join-Path $PerfArtifactsDirectory 'load'
New-Item -ItemType Directory -Force -Path $loadArtifactDirectory | Out-Null
$campaign = [ordered]@{
    schemaVersion = 1
    profile = 'backend-load'
    campaignId = $campaignId
    runCount = $RunCount
    cooldownSeconds = if ($SkipCooldown) { 0 } else { $CooldownSeconds }
    startedAt = [DateTimeOffset]::UtcNow.ToString('o')
    completedAt = $null
    status = 'RUNNING'
    runs = @()
}
$manifestPath = Join-Path $loadArtifactDirectory "$campaignId-campaign.json"

try {
    for ($runNumber = 1; $runNumber -le $RunCount; $runNumber++) {
        if ($runNumber -gt 1 -and -not $SkipCooldown -and $CooldownSeconds -gt 0) {
            Write-Host "Enfriamiento entre corridas: $CooldownSeconds segundos."
            Start-Sleep -Seconds $CooldownSeconds
        }

        & (Join-Path $repositoryRoot 'scripts\perf-environment-up.ps1') -SkipBuild
        & (Join-Path $repositoryRoot 'scripts\run-perf-baseline.ps1') -ValidateOnly

        $runId = [Guid]::NewGuid().ToString()
        $runStartedAt = [DateTimeOffset]::UtcNow
        Write-Host "Carga backend $runNumber/$RunCount. RunId: $runId"
        $k6Failure = $null
        try {
            & (Join-Path $repositoryRoot 'scripts\run-perf-k6.ps1') -Script backend-load.js -RunId $runId
        } catch {
            $k6Failure = $_
        }

        $containerSummaryPath = Join-Path $PerfArtifactsDirectory "$runId-load-summary.json"
        $summaryPath = Join-Path $loadArtifactDirectory "$runId-summary.json"
        if (Test-Path -LiteralPath $containerSummaryPath -PathType Leaf) {
            Move-Item -LiteralPath $containerSummaryPath -Destination $summaryPath -Force
        }
        if (-not (Test-Path -LiteralPath $summaryPath -PathType Leaf)) {
            throw "k6 no produjo el summary comparable $summaryPath"
        }
        $invariantPath = Join-Path $loadArtifactDirectory "$runId-invariants.txt"
        $invariantFailure = $null
        try {
            & (Join-Path $PSScriptRoot 'validate-performance-invariants.ps1') -OutputPath $invariantPath
        } catch {
            $invariantFailure = $_
        }

        $summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
        $runResult = [ordered]@{
            runNumber = $runNumber
            runId = $runId
            startedAt = $runStartedAt.ToString('o')
            completedAt = [DateTimeOffset]::UtcNow.ToString('o')
            summaryPath = $summaryPath
            invariantPath = $invariantPath
            requestCount = $summary.metrics.http_reqs.count
            p95Milliseconds = $summary.metrics.http_req_duration.'p(95)'
            p99Milliseconds = $summary.metrics.http_req_duration.'p(99)'
            status = if ($null -eq $k6Failure -and $null -eq $invariantFailure) { 'PASSED' } else { 'FAILED' }
        }
        $campaign.runs += $runResult
        [IO.File]::WriteAllText($manifestPath, ($campaign | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
        if ($null -ne $k6Failure) {
            throw 'k6 rechazó la corrida; se conservaron summary e invariantes.'
        }
        if ($null -ne $invariantFailure) {
            throw 'Las invariantes rechazaron la corrida; se conservaron summary e invariantes.'
        }
    }
    $campaign.status = 'COMPLETED'
    $campaign.completedAt = [DateTimeOffset]::UtcNow.ToString('o')
} catch {
    $campaign.status = 'FAILED'
    $campaign.completedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $campaign.error = 'Load run failed. Review the local console output.'
    throw
} finally {
    [IO.File]::WriteAllText($manifestPath, ($campaign | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
}

Write-Host "Campaña de carga completada: $manifestPath"
