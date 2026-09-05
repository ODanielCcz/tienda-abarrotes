$ErrorActionPreference = 'Stop'

function Test-PerfStressEvidence {
    param($RecoverySeconds, [int]$ValidDuringSamples, [int]$FailedDuringSamples)
    return $null -ne $RecoverySeconds -and [double]$RecoverySeconds -ge 0 -and
        [double]$RecoverySeconds -le 60 -and $ValidDuringSamples -gt 0 -and
        $FailedDuringSamples -eq 0
}

# Shared capture for /actuator/prometheus, docker stats, the read-only
# postgres-snapshot.sql query and /actuator/health/readiness.
function Get-PerfStressServiceContainers {
    param([Parameter(Mandatory = $true)]$Contract)

    $containers = [ordered]@{}
    foreach ($service in @($Contract.requiredServices)) {
        $containers[$service] = Get-PerfServiceContainerId -Service $service
    }
    return [pscustomobject]$containers
}

function New-PerfStressSnapshot {
    param(
        [Parameter(Mandatory = $true)][string]$Phase,
        [Parameter(Mandatory = $true)]$Contract,
        [Parameter(Mandatory = $true)][string]$BearerToken,
        [Parameter(Mandatory = $true)]$ServiceContainers
    )

    $prometheus = Get-PerfPrometheusSnapshot -Contract $Contract -BearerToken $BearerToken
    $dockerStats = Get-PerfDockerStats -ServiceContainers $ServiceContainers
    $postgres = Get-PerfPostgresSnapshot
    $correlationId = "stress-readiness-$([Guid]::NewGuid().ToString('N'))"
    $readiness = Invoke-PerfHttpGet `
        -Uri "http://127.0.0.1:$($Contract.ports.backend)/actuator/health/readiness" `
        -CorrelationId $correlationId `
        -TimeoutSeconds ([int]$Contract.timeouts.httpSeconds)
    $readinessBody = (ConvertFrom-PerfHttpContent -Content $readiness.response.Content) |
        ConvertFrom-Json
    $snapshot = [pscustomobject]@{
        capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
        phase = $Phase
        readiness = [pscustomobject]@{
            status = [string]$readinessBody.status
            httpStatus = [int]$readiness.response.StatusCode
            durationMs = [double]$readiness.durationMs
        }
        prometheus = $prometheus.selected
        prometheusScrapeDurationMs = [double]$prometheus.durationMs
        docker = @($dockerStats)
        postgres = $postgres
    }
    Assert-PerfStressSnapshot -Snapshot $snapshot -Contract $Contract
    return $snapshot
}

function Assert-PerfStressSnapshot {
    param(
        [Parameter(Mandatory = $true)]$Snapshot,
        [Parameter(Mandatory = $true)]$Contract
    )

    if ($Snapshot.phase -cne 'during' -and
        ($Snapshot.readiness.httpStatus -ne 200 -or $Snapshot.readiness.status -cne 'UP')) {
        throw 'Readiness no está UP durante la captura de stress.'
    }
    foreach ($metric in @($Contract.prometheusMetrics.critical)) {
        if (-not (Test-PerfFiniteNumber -Value $Snapshot.prometheus.$metric -NonNegative)) {
            throw "La métrica crítica $metric no está disponible durante stress."
        }
    }
    if ($Snapshot.postgres.databaseName -cne $Contract.database.name -or
        $Snapshot.postgres.databaseUser -cne $Contract.database.owner) {
        throw 'La instantánea PostgreSQL no pertenece al ambiente de rendimiento.'
    }
    foreach ($service in @($Contract.requiredServices)) {
        if (@($Snapshot.docker | Where-Object service -ceq $service).Count -ne 1) {
            throw "La captura Docker no contiene exactamente una fila para $service."
        }
    }
}

function Add-PerfStressSnapshot {
    param(
        [Parameter(Mandatory = $true)]$Snapshot,
        [Parameter(Mandatory = $true)][string]$Path
    )

    Assert-PerfArtifactPath -Path $Path
    [IO.File]::AppendAllText(
        $Path,
        (($Snapshot | ConvertTo-Json -Depth 12 -Compress) + [Environment]::NewLine),
        [Text.UTF8Encoding]::new($false)
    )
}

function Get-PerfStressResourceSummary {
    param([Parameter(Mandatory = $true)][object[]]$Snapshots)

    $first = $Snapshots[0]
    $last = $Snapshots[-1]
    $backendRows = @($Snapshots | ForEach-Object {
        $_.docker | Where-Object service -ceq 'backend'
    })
    $poolRatios = @($Snapshots | ForEach-Object {
        $maximum = [double]$_.prometheus.hikaricp_connections_max
        if ($maximum -gt 0) {
            100.0 * [double]$_.prometheus.hikaricp_connections_active / $maximum
        }
    })
    $startHeap = [double]$first.prometheus.jvm_memory_used_bytes
    $endHeap = [double]$last.prometheus.jvm_memory_used_bytes
    return [pscustomobject]@{
        sampleCount = $Snapshots.Count
        backendMemoryBytes = [pscustomobject]@{
            first = [double]$backendRows[0].memoryUsedBytes
            last = [double]$backendRows[-1].memoryUsedBytes
            peak = [double](($backendRows.memoryUsedBytes | Measure-Object -Maximum).Maximum)
        }
        jvmMemoryUsedBytes = [pscustomobject]@{
            first = $startHeap
            last = $endHeap
            deltaPercent = if ($startHeap -gt 0) {
                [Math]::Round((($endHeap - $startHeap) / $startHeap) * 100.0, 3)
            } else { $null }
        }
        jvmThreads = [pscustomobject]@{
            first = [double]$first.prometheus.jvm_threads_live_threads
            last = [double]$last.prometheus.jvm_threads_live_threads
            peak = [double](($Snapshots.prometheus.jvm_threads_live_threads |
                Measure-Object -Maximum).Maximum)
        }
        hikari = [pscustomobject]@{
            peakUtilizationPercent = if ($poolRatios.Count -gt 0) {
                [Math]::Round((($poolRatios | Measure-Object -Maximum).Maximum), 3)
            } else { $null }
            finalActive = [double]$last.prometheus.hikaricp_connections_active
            finalIdle = [double]$last.prometheus.hikaricp_connections_idle
            finalPending = [double]$last.prometheus.hikaricp_connections_pending
        }
        postgres = [pscustomobject]@{
            finalSessions = [int]$last.postgres.activity.total_sessions
            finalWaitingLocks = [int]$last.postgres.locks.waiting_locks
            finalDeadlocks = [int64]$last.postgres.database.deadlocks
        }
    }
}
