[CmdletBinding()]
param(
    [ValidateSet('stress', 'spike', 'soak')]
    [string[]]$Profiles = @('stress', 'spike', 'soak'),
    [ValidateRange(1, 60)]
    [int]$SampleIntervalSeconds = 5,
    [ValidateRange(0, 900)]
    [int]$CooldownSeconds = 60,
    [switch]$Quick,
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $repositoryRoot 'scripts\_perf-common.ps1')
. (Join-Path $repositoryRoot 'scripts\_perf-baseline.ps1')
. (Join-Path $PSScriptRoot 'capture-performance-snapshot.ps1')

$validator = Join-Path $repositoryRoot 'tests\performance\k6\validate-stress.ps1'
& $validator
if ($ValidateOnly) {
    foreach ($profile in $Profiles) {
        & (Join-Path $repositoryRoot 'scripts\run-perf-k6.ps1') `
            -Command inspect -Script "backend-$profile.js" -QuickProfile:$Quick
    }
    return
}

Assert-PerfDockerAvailable
New-PerfEnvironmentFile
Import-PerfEnvironment
$contract = Get-PerfBaselineContract
$gitStatus = & git -C $repositoryRoot status --porcelain
if ($LASTEXITCODE -ne 0) { throw 'No fue posible comprobar el estado Git.' }
if (-not $Quick -and @($gitStatus).Count -gt 0) {
    throw 'La campaña oficial requiere un árbol Git limpio. Usa Quick para diagnóstico.'
}
$campaignId = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$artifactRoot = Join-Path $PerfArtifactsDirectory "stress\$campaignId"
Assert-PerfArtifactPath -Path $artifactRoot
New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
$manifestPath = Join-Path $artifactRoot 'campaign.json'
$campaign = [ordered]@{
    schemaVersion = 1
    campaignId = $campaignId
    profiles = @($Profiles)
    quick = [bool]$Quick
    sampleIntervalSeconds = if ($Quick) { 1 } else { $SampleIntervalSeconds }
    startedAt = [DateTimeOffset]::UtcNow.ToString('o')
    completedAt = $null
    status = 'RUNNING'
    gitRevision = (& git -C $repositoryRoot rev-parse HEAD | Out-String).Trim()
    gitDirtyEntryCount = @($gitStatus).Count
    results = @()
}
$campaign | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM

$campaignFailure = $null
try {
foreach ($profile in $Profiles) {
    if ($campaign.results.Count -gt 0 -and -not $Quick -and $CooldownSeconds -gt 0) {
        Write-Host "Enfriamiento antes de ${profile}: $CooldownSeconds segundos."
        Start-Sleep -Seconds $CooldownSeconds
    }

    $profileDirectory = Join-Path $artifactRoot $profile
    New-Item -ItemType Directory -Force -Path $profileDirectory | Out-Null
    $samplesPath = Join-Path $profileDirectory 'samples.jsonl'
    $consolePath = Join-Path $profileDirectory 'k6-console.txt'
    $summaryPath = Join-Path $profileDirectory 'summary.json'
    $invariantPath = Join-Path $profileDirectory 'invariants.txt'
    $runId = [Guid]::NewGuid().ToString()
    $startedAt = [DateTimeOffset]::UtcNow
    $job = $null
    $bearerToken = $null
    $k6Result = $null
    $k6Summary = $null
    $invariantPassed = $false
    $invariantAttempted = $false
    $after = $null
    $validDuringSamples = 0
    $failedDuringSamples = 0
    $recoverySeconds = $null
    $snapshots = [System.Collections.Generic.List[object]]::new()

    try {
        # Build once from the official clean revision; reuse that image between profiles.
        $skipBuild = $Quick -or $campaign.results.Count -gt 0
        & (Join-Path $repositoryRoot 'scripts\perf-environment-up.ps1') -SkipBuild:$skipBuild
        & (Join-Path $repositoryRoot 'scripts\run-perf-baseline.ps1') -ValidateOnly
        Initialize-PerfPostgresObservability
        $serviceContainers = Get-PerfStressServiceContainers -Contract $contract
        $metadata = Get-PerfEnvironmentMetadata -ServiceContainers $serviceContainers
        $jars = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'apps\backend\build\libs') -Filter '*.jar')
        if ($jars.Count -ne 1) { throw 'Se requiere exactamente un JAR backend para verificar procedencia.' }
        $hostJarHash = (Get-FileHash -LiteralPath $jars[0].FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $containerHashResult = Invoke-PerfDockerProcess -Arguments @(
            'exec', $serviceContainers.backend, 'sha256sum', '/app/tienda-abarrotes-backend.jar'
        ) -Stage 'stress-backend-jar-hash'
        $containerJarHash = ($containerHashResult.stdout -split '\s+')[0]
        if ($containerHashResult.exitCode -ne 0 -or $hostJarHash -cne $containerJarHash) {
            throw 'El JAR del contenedor no coincide con el build local.'
        }
        $imageResult = Invoke-PerfDockerProcess -Arguments @(
            'image', 'inspect', $metadata.containers.backend.imageId
        ) -Stage 'stress-backend-image'
        if ($imageResult.exitCode -ne 0) { throw 'No fue posible registrar la imagen backend.' }
        $imageInfo = @($imageResult.stdout | ConvertFrom-Json)[0]
        $provenance = [pscustomobject]@{
            metadata = $metadata
            hostJarSha256 = $hostJarHash
            containerJarSha256 = $containerJarHash
            imageId = $imageInfo.Id
            imageCreatedAt = $imageInfo.Created
            repoDigests = @($imageInfo.RepoDigests)
            rebuiltForCampaign = -not [bool]$Quick
        }
        $bearerToken = Get-PerfBearerToken -Contract $contract

        $before = New-PerfStressSnapshot -Phase 'before' -Contract $contract `
            -BearerToken $bearerToken -ServiceContainers $serviceContainers
        $snapshots.Add($before)
        Add-PerfStressSnapshot -Snapshot $before -Path $samplesPath

        Write-Host "Ejecutando perfil $profile. RunId: $runId"
        $job = Start-Job -ArgumentList @(
            $repositoryRoot, $profile, $runId, [bool]$Quick, $consolePath
        ) -ScriptBlock {
            param($root, $profileName, $profileRunId, $quickMode, $logPath)
            Set-Location -LiteralPath $root
            try {
                & (Join-Path $root 'scripts\run-perf-k6.ps1') `
                    -Script "backend-$profileName.js" -RunId $profileRunId `
                    -QuickProfile:$quickMode *>&1 |
                    Out-File -LiteralPath $logPath -Encoding utf8
                [pscustomobject]@{ success = $true; exitCode = 0; error = $null }
            }
            catch {
                $_ | Out-String | Add-Content -LiteralPath $logPath -Encoding utf8
                [pscustomobject]@{
                    success = $false
                    exitCode = $_.Exception.Data['NativeExitCode']
                    error = $_.Exception.Message
                }
            }
        }

        while ($job.State -in @('NotStarted', 'Running')) {
            Start-Sleep -Seconds $(if ($Quick) { 1 } else { $SampleIntervalSeconds })
            $job = Get-Job -Id $job.Id
            if ($job.State -in @('NotStarted', 'Running')) {
                try {
                    $during = New-PerfStressSnapshot -Phase 'during' -Contract $contract `
                        -BearerToken $bearerToken -ServiceContainers $serviceContainers
                    $snapshots.Add($during)
                    $validDuringSamples++
                } catch {
                    $failedDuringSamples++
                    $during = [pscustomobject]@{
                        capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
                        phase = 'during'
                        captureStatus = 'UNAVAILABLE'
                        errorCode = 'OBSERVABILITY_CAPTURE_FAILED'
                    }
                }
                Add-PerfStressSnapshot -Snapshot $during -Path $samplesPath
            }
        }
        $k6Result = @(Receive-Job -Job $job -ErrorAction SilentlyContinue | Select-Object -Last 1)[0]

        $recoveryTimer = [Diagnostics.Stopwatch]::StartNew()
        $trafficStoppedAt = $null
        $stopMarker = Select-String -LiteralPath $consolePath -Pattern 'PERF_TRAFFIC_STOPPED_AT=(\d+)' |
            Select-Object -Last 1
        if ($null -ne $stopMarker) {
            $trafficStoppedAt = [DateTimeOffset]::FromUnixTimeMilliseconds(
                [long]$stopMarker.Matches[0].Groups[1].Value)
        }
        while ($null -ne $trafficStoppedAt -and
            ([DateTimeOffset]::UtcNow - $trafficStoppedAt).TotalSeconds -le 60) {
            try {
                $correlationId = "stress-recovery-$([Guid]::NewGuid().ToString('N'))"
                $readiness = Invoke-PerfHttpGet `
                    -Uri "http://127.0.0.1:$($contract.ports.backend)/actuator/health/readiness" `
                    -CorrelationId $correlationId -TimeoutSeconds 5
                $body = (ConvertFrom-PerfHttpContent -Content $readiness.response.Content) |
                    ConvertFrom-Json
                if ($readiness.response.StatusCode -eq 200 -and $body.status -ceq 'UP') {
                    $recoverySeconds = [Math]::Round(
                        ([DateTimeOffset]::UtcNow - $trafficStoppedAt).TotalSeconds, 3)
                    break
                }
            }
            catch { }
            Start-Sleep -Seconds 1
        }
        $recoveryTimer.Stop()

        try {
            $after = New-PerfStressSnapshot -Phase 'after' -Contract $contract `
                -BearerToken $bearerToken -ServiceContainers $serviceContainers
            $snapshots.Add($after)
            Add-PerfStressSnapshot -Snapshot $after -Path $samplesPath
        } catch {
            Add-PerfStressSnapshot -Path $samplesPath -Snapshot ([pscustomobject]@{
                capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
                phase = 'after'
                captureStatus = 'UNAVAILABLE'
                errorCode = 'OBSERVABILITY_CAPTURE_FAILED'
            })
        }

        $rawSummaryPath = Join-Path $PerfArtifactsDirectory "$runId-backend-$profile-summary.json"
        if (Test-Path -LiteralPath $rawSummaryPath -PathType Leaf) {
            Move-Item -LiteralPath $rawSummaryPath -Destination $summaryPath -Force
            $k6Summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
        }

        try {
            $invariantAttempted = $true
            & (Join-Path $PSScriptRoot 'validate-performance-invariants.ps1') `
                -OutputPath $invariantPath
            $invariantPassed = $true
        }
        catch {
            $invariantPassed = $false
        }

        $resourceSummary = Get-PerfStressResourceSummary -Snapshots @($snapshots)
        $evidenceComplete = Test-PerfStressEvidence -RecoverySeconds $recoverySeconds `
            -ValidDuringSamples $validDuringSamples -FailedDuringSamples $failedDuringSamples
        $k6Passed = $null -ne $k6Result -and [bool]$k6Result.success
        $resultStatus = if ($k6Passed -and $null -ne $after -and $null -ne $k6Summary -and
            $invariantPassed -and $evidenceComplete) {
            'PASSED'
        } elseif ($null -ne $after -and $null -ne $k6Result -and $k6Result.exitCode -eq 99 -and
            $null -ne $k6Summary -and $invariantPassed -and $evidenceComplete) {
            'THRESHOLD_BREACHED'
        } else {
            'FAILED'
        }
        $result = [ordered]@{
            profile = $profile
            provenance = $provenance
            runId = $runId
            startedAt = $startedAt.ToString('o')
            completedAt = [DateTimeOffset]::UtcNow.ToString('o')
            status = $resultStatus
            k6Passed = $k6Passed
            k6ExitCode = if ($null -ne $k6Result) { $k6Result.exitCode } else { $null }
            k6Error = if ($k6Passed) { $null } else { [string]$k6Result.error }
            requestCount = if ($null -ne $k6Summary) { $k6Summary.metrics.http_reqs.count } else { $null }
            p95Milliseconds = if ($null -ne $k6Summary) { $k6Summary.metrics.http_req_duration.'p(95)' } else { $null }
            p99Milliseconds = if ($null -ne $k6Summary) { $k6Summary.metrics.http_req_duration.'p(99)' } else { $null }
            lastStableTarget = if ($null -ne $k6Summary) { $k6Summary.lastStableTarget } else { $null }
            stressLevels = if ($null -ne $k6Summary) { $k6Summary.levels } else { $null }
            readinessRecoverySeconds = $recoverySeconds
            validDuringSamples = $validDuringSamples
            failedDuringSamples = $failedDuringSamples
            evidenceComplete = $evidenceComplete
            invariantsPassed = $invariantPassed
            invariantArtifactPath = $invariantPath
            resources = $resourceSummary
            artifactDirectory = $profileDirectory
        }
        $campaign.results += $result
        $campaign | ConvertTo-Json -Depth 12 |
            Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
        if ($resultStatus -eq 'FAILED') {
            throw 'El perfil falló; se conserva el ambiente para diagnóstico y no se inicia otro perfil.'
        }
    }
    finally {
        if ($null -ne $job) { Remove-Job -Job $job -Force -ErrorAction SilentlyContinue }
        if (-not $invariantAttempted) {
            try {
                & (Join-Path $PSScriptRoot 'validate-performance-invariants.ps1') -OutputPath $invariantPath
            } catch {
                if (-not (Test-Path -LiteralPath $invariantPath)) {
                    [IO.File]::WriteAllText($invariantPath, 'FAIL: INVARIANT_VALIDATION_UNAVAILABLE')
                }
            }
        }
        $bearerToken = $null
    }
}
}
catch {
    $campaignFailure = $_
}

if ($null -ne $campaignFailure) {
    $campaign.completedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $campaign.status = 'FAILED'
    $campaign.error = 'Stress campaign failed. Review the sanitized local artifacts.'
    $campaign | ConvertTo-Json -Depth 12 |
        Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
    throw $campaignFailure
}

$campaign.completedAt = [DateTimeOffset]::UtcNow.ToString('o')
$campaign.status = if (@($campaign.results | Where-Object status -eq 'FAILED').Count -gt 0) {
    'FAILED'
} elseif (@($campaign.results | Where-Object status -eq 'THRESHOLD_BREACHED').Count -gt 0) {
    'COMPLETED_WITH_FINDINGS'
} else {
    'COMPLETED'
}
$campaign | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM

Write-Host "Campaña stress/spike/soak finalizada: $manifestPath"
if ($campaign.status -eq 'FAILED') {
    throw 'La campaña contiene un fallo de infraestructura, evidencia o integridad.'
}
