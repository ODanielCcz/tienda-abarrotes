[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $repositoryRoot 'scripts\_perf-common.ps1')
. (Join-Path $repositoryRoot 'scripts\_perf-baseline.ps1')

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message Esperado=[$Expected], actual=[$Actual]."
    }
}

# Regression: PowerShell 7.6 may expose Invoke-WebRequest.Content as byte[].
# Replacing the decoder with a direct [string] cast must break this test.
$jsonBytes = [Text.Encoding]::UTF8.GetBytes('{"status":"UP"}')
$decoded = ConvertFrom-PerfHttpContent -Content $jsonBytes
Assert-Equal -Expected 'UP' -Actual (($decoded | ConvertFrom-Json).status) `
    -Message 'El contenido HTTP binario debe decodificarse como UTF-8.'

Assert-Equal -Expected '{"status":"UP"}' `
    -Actual (ConvertFrom-PerfHttpContent -Content '{"status":"UP"}') `
    -Message 'El contenido HTTP de texto debe preservarse.'

# Regression: Prometheus rejects the generic JSON Accept header with 406.
$prometheusHeaders = New-PerfHttpHeaders `
    -CorrelationId 'baseline-test' `
    -BearerToken 'synthetic-token' `
    -Accept 'text/plain'
Assert-Equal -Expected 'text/plain' -Actual $prometheusHeaders.Accept `
    -Message 'Prometheus debe poder solicitar text/plain.'
Assert-Equal -Expected 'Bearer synthetic-token' -Actual $prometheusHeaders.Authorization `
    -Message 'El helper debe conservar la autenticación en memoria.'

$queryPath = New-PerfQueryPath `
    -Path '/api/v1/inventory/stock/95100000-0000-0000-0001-000000000001' `
    -Parameters ([ordered]@{ warehouseId = '90000000-0000-0000-0000-000000000011' })
Assert-Equal `
    -Expected '/api/v1/inventory/stock/95100000-0000-0000-0001-000000000001?warehouseId=90000000-0000-0000-0000-000000000011' `
    -Actual $queryPath `
    -Message 'Los parámetros no deben consumir el UUID anterior durante interpolación.'

Assert-Equal -Expected 2.5 -Actual (Get-PerfMedian -Values @(1.0, 2.0, 3.0, 4.0)) `
    -Message 'La mediana par debe promediar los dos valores centrales.'

$prometheus = @'
# HELP hikaricp_connections_active Active connections
hikaricp_connections_active{pool="HikariPool-1"} 2
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 100
jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 300
'@
$metrics = ConvertFrom-PerfPrometheus -Content $prometheus `
    -MetricFamilies @('hikaricp_connections_active', 'jvm_memory_used_bytes', 'missing_metric')
Assert-Equal -Expected 2.0 -Actual $metrics.hikaricp_connections_active `
    -Message 'Debe leer un gauge Prometheus.'
Assert-Equal -Expected 400.0 -Actual $metrics.jvm_memory_used_bytes `
    -Message 'Debe sumar las series de una familia Prometheus.'
if ($null -ne $metrics.missing_metric) {
    throw 'Una familia ausente debe permanecer null, no confundirse con cero.'
}

Assert-Equal -Expected 1572864 -Actual (Convert-PerfSizeToBytes -Value '1.5MiB') `
    -Message 'Debe normalizar memoria binaria a bytes.'

Assert-PerfArtifactPath -Path (Join-Path $PerfArtifactsDirectory 'baseline\unit-test')
$repoArtifactRejected = $false
try {
    Assert-PerfArtifactPath -Path (Join-Path $PerfProjectRoot 'artifacts\baseline')
}
catch {
    $repoArtifactRejected = $true
}
if (-not $repoArtifactRejected) {
    throw 'La protección debe rechazar artefactos crudos dentro del repositorio/OneDrive.'
}

$contract = Get-PerfBaselineContract

function New-SyntheticServiceInspection {
    param(
        [string]$Service,
        [bool]$Running = $true,
        [string]$Health = 'healthy',
        [int]$RestartCount = 0
    )
    return [pscustomobject]@{
        Config = [pscustomobject]@{
            Labels = [pscustomobject]@{
                'com.docker.compose.project' = $PerfProjectName
                'com.docker.compose.service' = $Service
            }
        }
        State = [pscustomobject]@{
            Running = $Running
            Health = [pscustomobject]@{ Status = $Health }
        }
        RestartCount = $RestartCount
    }
}

Assert-PerfServiceInspectionSnapshot `
    -Inspection (New-SyntheticServiceInspection -Service 'backend') `
    -ExpectedService 'backend' -ExpectedRestartCount 0

$stoppedRejected = $false
try {
    Assert-PerfServiceInspectionSnapshot `
        -Inspection (New-SyntheticServiceInspection -Service 'backend' -Running $false) `
        -ExpectedService 'backend' -ExpectedRestartCount 0
}
catch { $stoppedRejected = $true }
if (-not $stoppedRejected) { throw 'Una muestra debe rechazar un backend detenido.' }

$unhealthyRejected = $false
try {
    Assert-PerfServiceInspectionSnapshot `
        -Inspection (New-SyntheticServiceInspection -Service 'redis' -Health 'unhealthy') `
        -ExpectedService 'redis' -ExpectedRestartCount 0
}
catch { $unhealthyRejected = $true }
if (-not $unhealthyRejected) { throw 'Una muestra debe rechazar un Redis unhealthy.' }

$restartRejected = $false
try {
    Assert-PerfServiceInspectionSnapshot `
        -Inspection (New-SyntheticServiceInspection -Service 'web-performance' -RestartCount 1) `
        -ExpectedService 'web-performance' -ExpectedRestartCount 0
}
catch { $restartRejected = $true }
if (-not $restartRejected) { throw 'Una muestra debe rechazar un restart no observado en preflight.' }

$samples = @(0..60 | ForEach-Object {
    $value = [double]$_
    $prometheusValues = [ordered]@{}
    foreach ($metric in @($contract.prometheusMetrics.critical)) {
        $prometheusValues[$metric] = $value + 1.0
    }
    foreach ($metric in @($contract.prometheusMetrics.optional)) {
        $prometheusValues[$metric] = $value + 2.0
    }
    [pscustomobject]@{
        readiness = [pscustomobject]@{ status = 'UP'; durationMs = 1.0 }
        prometheus = [pscustomobject]$prometheusValues
        prometheusScrapeDurationMs = 2.0
        timing = [pscustomobject]@{
            scheduledAt = ([DateTimeOffset]'2026-08-30T12:00:00Z').AddSeconds($value * 5).ToString('o')
            actualAt = ([DateTimeOffset]'2026-08-30T12:00:00Z').AddSeconds($value * 5).ToString('o')
            jitterMilliseconds = 0.0
            captureDurationMilliseconds = 10.0
        }
        docker = @($contract.requiredServices | ForEach-Object {
            [pscustomobject]@{
                service = $_
                cpuPercent = $value
                memoryUsedBytes = 1000 + $value
                memoryPercent = $value / 10
                pids = 10 + $value
            }
        })
        postgres = [pscustomobject]@{
            databaseName = $contract.database.name
            databaseUser = $contract.database.owner
            database = [pscustomobject]@{
                numbackends = 5
                xact_commit = $value
                xact_rollback = $value
                blks_read = $value
                blks_hit = 100 + $value
                temp_files = 0
                temp_bytes = 0
                deadlocks = 0
            }
            activity = [pscustomobject]@{
                active_sessions = 1
                waiting_sessions = 0
                total_sessions = 2
            }
            locks = [pscustomobject]@{ granted_locks = 3; waiting_locks = 0 }
            topStatements = @()
        }
    }
})
$official = Get-PerfPostWarmMedian -Samples $samples -Contract $contract
Assert-Equal -Expected 61 -Actual $official.sampleCount `
    -Message 'La mediana oficial exige la ventana postWarm completa.'
Assert-Equal -Expected 31.0 -Actual $official.prometheus.process_cpu_usage.median `
    -Message 'La mediana postWarm debe usar todas las muestras, no la mejor.'
Assert-Equal -Expected 60.0 -Actual $official.postgres.xactRollbacks.delta `
    -Message 'Los contadores PostgreSQL deben conservar el delta de la ventana.'

$invalidSamples = @($samples)
$invalidSamples[0].prometheus.process_cpu_usage = $null
$criticalMetricRejected = $false
try {
    [void](Get-PerfPostWarmMedian -Samples $invalidSamples -Contract $contract)
}
catch {
    $criticalMetricRejected = $true
}
if (-not $criticalMetricRejected) {
    throw 'Una métrica crítica null debe invalidar toda la ventana oficial.'
}
$invalidSamples[0].prometheus.process_cpu_usage = 1.0

$originalDockerRows = $samples[0].docker
$duplicateDockerRejected = $false
try {
    $samples[0].docker = @($originalDockerRows) + @($originalDockerRows[0])
    Assert-PerfMetricSample -Sample $samples[0] -Contract $contract
}
catch { $duplicateDockerRejected = $true }
finally { $samples[0].docker = $originalDockerRows }
if (-not $duplicateDockerRejected) {
    throw 'Una fila Docker duplicada debe invalidar la muestra.'
}

$originalDatabaseName = $samples[0].postgres.databaseName
$invalidPostgresRejected = $false
try {
    $samples[0].postgres.databaseName = 'unexpected_database'
    Assert-PerfMetricSample -Sample $samples[0] -Contract $contract
}
catch { $invalidPostgresRejected = $true }
finally { $samples[0].postgres.databaseName = $originalDatabaseName }
if (-not $invalidPostgresRejected) {
    throw 'Una identidad PostgreSQL inválida debe invalidar la muestra.'
}

$diagnosticSummary = New-PerfBaselineSummary `
    -SkipProjectVerification `
    -StartedAt ([DateTimeOffset]'2026-08-30T10:00:00Z') `
    -CompletedAt ([DateTimeOffset]'2026-08-30T10:11:00Z') `
    -Contract $contract `
    -Environment ([pscustomobject]@{ gitRevision = 'abc123' }) `
    -AngularBuild ([pscustomobject]@{ fileCount = 1; totalBytes = 100 }) `
    -PreWarmSampleCount 61 `
    -WarmupSummary @() `
    -PostWarmStatistics ([pscustomobject]@{ sampleCount = 61 })
Assert-Equal -Expected 'DIAGNOSTIC' -Actual $diagnosticSummary.status `
    -Message 'SkipProjectVerification debe producir estado DIAGNOSTIC.'
Assert-Equal -Expected $false -Actual $diagnosticSummary.official `
    -Message 'Una ejecución sin suites nunca puede declararse oficial.'
if ($null -ne $diagnosticSummary.PSObject.Properties['officialBaseline']) {
    throw 'El resumen diagnóstico no debe exponer el campo officialBaseline.'
}
if ($null -eq $diagnosticSummary.PSObject.Properties['diagnosticBaseline']) {
    throw 'El resumen diagnóstico debe usar diagnosticBaseline.'
}

$officialSummary = New-PerfBaselineSummary `
    -StartedAt ([DateTimeOffset]'2026-08-30T10:00:00Z') `
    -CompletedAt ([DateTimeOffset]'2026-08-30T10:11:00Z') `
    -Contract $contract `
    -Environment ([pscustomobject]@{ gitRevision = 'abc123' }) `
    -AngularBuild ([pscustomobject]@{ fileCount = 1; totalBytes = 100 }) `
    -PreWarmSampleCount 61 `
    -WarmupSummary @() `
    -PostWarmStatistics ([pscustomobject]@{ sampleCount = 61 })
Assert-Equal -Expected 'COMPLETED' -Actual $officialSummary.status `
    -Message 'La captura con suites debe terminar como COMPLETED.'
Assert-Equal -Expected $true -Actual $officialSummary.official `
    -Message 'Solo la captura con suites puede marcarse oficial.'
if ($null -eq $officialSummary.PSObject.Properties['officialBaseline'] -or
    $null -ne $officialSummary.PSObject.Properties['diagnosticBaseline']) {
    throw 'El resumen oficial debe exponer únicamente officialBaseline.'
}

$scheduledStart = [DateTimeOffset]'2026-08-30T12:00:00Z'
Assert-Equal `
    -Expected '2026-08-30T12:00:15.0000000+00:00' `
    -Actual ((Get-PerfScheduledSampleTime -WindowStart $scheduledStart -Index 3 -IntervalSeconds 5).ToString('o')) `
    -Message 'La cadencia debe derivarse del inicio objetivo y evitar drift acumulado.'

$previousPassword = $env:PERF_PASSWORD
$env:PERF_PASSWORD = 'must-not-be-inherited'
try {
    $safeEnvironment = Get-PerfSafeEnvironment
    if ($safeEnvironment.ContainsKey('PERF_PASSWORD')) {
        throw 'El entorno mínimo no debe heredar contraseñas del proyecto.'
    }
}
finally {
    $env:PERF_PASSWORD = $previousPassword
}

$timeoutRejected = $false
$timeoutTimer = [Diagnostics.Stopwatch]::StartNew()
try {
    [void](Invoke-PerfNativeProcess `
        -FilePath ([Diagnostics.Process]::GetCurrentProcess().MainModule.FileName) `
        -Arguments @('-NoProfile', '-Command', 'Start-Sleep -Seconds 5') `
        -TimeoutSeconds 1 `
        -Stage 'synthetic-timeout')
}
catch {
    $timeoutRejected = $_.Exception.Message -match 'timeout controlado'
}
$timeoutTimer.Stop()
if (-not $timeoutRejected -or $timeoutTimer.Elapsed.TotalSeconds -gt 4) {
    throw 'Un proceso externo bloqueado debe terminar dentro del timeout controlado.'
}

$artifactTestDirectory = Join-Path $PerfArtifactsDirectory (
    'baseline\helper-test-' + [Guid]::NewGuid().ToString('N')
)
Assert-PerfArtifactPath -Path $artifactTestDirectory
New-Item -ItemType Directory -Force -Path $artifactTestDirectory | Out-Null
$syntheticSecret = 'synthetic-secret-' + [Guid]::NewGuid().ToString('N')
$syntheticJwt = @(
    'eyJhbGciOiJIUzI1NiJ9',
    'eyJzdWIiOiJiYXNlbGluZS10ZXN0In0',
    'abcdefghijklmnopqrstuvwx'
) -join '.'
$previousArtifactPassword = $env:PERF_PASSWORD
$lock = $null
try {
    $env:PERF_PASSWORD = $syntheticSecret
    $batchDirectory = Join-Path $artifactTestDirectory 'batch with spaces'
    New-Item -ItemType Directory -Force -Path $batchDirectory | Out-Null
    $batchPath = Join-Path $batchDirectory 'probe.cmd'
    [IO.File]::WriteAllText(
        $batchPath,
        "@echo off`r`necho batch-wrapper-ok`r`n",
        [Text.ASCIIEncoding]::new()
    )
    $batchResult = Invoke-PerfNativeProcess -FilePath $batchPath `
        -TimeoutSeconds 5 -Stage 'synthetic-batch-wrapper'
    Assert-Equal -Expected 0 -Actual $batchResult.exitCode `
        -Message 'El wrapper debe ejecutar .cmd en rutas con espacios.'
    Assert-Equal -Expected 'batch-wrapper-ok' -Actual $batchResult.stdout.Trim() `
        -Message 'El wrapper .cmd debe preservar stdout.'

    [IO.File]::WriteAllText(
        (Join-Path $artifactTestDirectory 'raw.prom'),
        "secret=$syntheticSecret token=$syntheticJwt",
        [Text.UTF8Encoding]::new($false)
    )
    $sanitized = Protect-PerfArtifacts -ArtifactDirectory $artifactTestDirectory
    if ($sanitized.filesScanned -lt 2) {
        throw 'La sanitización debe recorrer todos los artefactos, sin filtrar extensión.'
    }
    $protectedContent = Get-Content -Raw -LiteralPath (Join-Path $artifactTestDirectory 'raw.prom')
    if ($protectedContent.Contains($syntheticSecret) -or $protectedContent.Contains($syntheticJwt)) {
        throw 'La sanitización no debe conservar secretos ni JWT en archivos crudos.'
    }

    $lockPath = Join-Path $artifactTestDirectory 'campaign.lock'
    $lock = Enter-PerfBaselineLock -LockPath $lockPath
    $secondLockRejected = $false
    try { [void](Enter-PerfBaselineLock -LockPath $lockPath) }
    catch { $secondLockRejected = $true }
    if (-not $secondLockRejected) {
        throw 'El lock exclusivo debe rechazar una segunda campaña simultánea.'
    }

    $directoryAsLock = Join-Path $artifactTestDirectory 'directory-is-not-a-lock-file'
    New-Item -ItemType Directory -Force -Path $directoryAsLock | Out-Null
    $nonSharingErrorPreserved = $false
    try { [void](Enter-PerfBaselineLock -LockPath $directoryAsLock) }
    catch {
        $nonSharingErrorPreserved = $_.Exception.Message -notmatch 'campaña de baseline activa'
    }
    if (-not $nonSharingErrorPreserved) {
        throw 'Un error de ruta/permisos no debe traducirse a campaña activa.'
    }

    $rootDeleteRejected = $false
    try { Remove-PerfArtifactDirectorySafely -ArtifactDirectory $PerfArtifactsDirectory }
    catch { $rootDeleteRejected = $true }
    if (-not $rootDeleteRejected -or -not (Test-Path -LiteralPath $PerfArtifactsDirectory)) {
        throw 'La raíz de artefactos nunca puede ser candidata de borrado.'
    }

    $foreignDirectory = Join-Path $PerfArtifactsDirectory (
        'baseline\foreign-' + [Guid]::NewGuid().ToString('N')
    )
    New-Item -ItemType Directory -Force -Path $foreignDirectory | Out-Null
    [IO.File]::WriteAllText((Join-Path $foreignDirectory 'sentinel.txt'), 'keep')
    $foreignRejected = $false
    try { Remove-PerfArtifactDirectorySafely -ArtifactDirectory $foreignDirectory }
    catch { $foreignRejected = $true }
    if (-not $foreignRejected -or -not (Test-Path -LiteralPath $foreignDirectory)) {
        throw 'Un nombre ajeno al patrón canónico debe rechazarse antes de borrar.'
    }
    [IO.Directory]::Delete($foreignDirectory, $true)

    $syntheticRunName = '20260830T123456789Z'
    $syntheticRun = Join-Path $PerfArtifactsDirectory "baseline\$syntheticRunName"
    $parentReparseRejected = $false
    try {
        Assert-PerfPathComponentsSafe `
            -AllowedRoot $PerfArtifactsDirectory `
            -TargetPath $syntheticRun `
            -PathInspector {
                param($Component)
                $attributes = if ((Split-Path -Leaf $Component) -ceq 'baseline') {
                    [IO.FileAttributes]::Directory -bor [IO.FileAttributes]::ReparsePoint
                } else { [IO.FileAttributes]::Directory }
                [pscustomobject]@{
                    Exists = $true
                    IsContainer = $true
                    Attributes = $attributes
                    FullName = $Component
                }
            }
    }
    catch { $parentReparseRejected = $true }
    if (-not $parentReparseRejected) {
        throw 'Un parent reparse/junction sintético debe rechazarse.'
    }

    $candidateReparseDirectory = New-PerfBaselineArtifactDirectory -Contract $contract
    [IO.File]::WriteAllText((Join-Path $candidateReparseDirectory 'sentinel.txt'), 'keep')
    $candidateReparseRejected = $false
    try {
        Remove-PerfArtifactDirectorySafely `
            -ArtifactDirectory $candidateReparseDirectory `
            -PathInspector {
                param($Component)
                $isCandidate = $Component -ceq $candidateReparseDirectory
                [pscustomobject]@{
                    Exists = $true
                    IsContainer = $true
                    Attributes = if ($isCandidate) {
                        [IO.FileAttributes]::Directory -bor [IO.FileAttributes]::ReparsePoint
                    } else { [IO.FileAttributes]::Directory }
                    FullName = $Component
                }
            }
    }
    catch { $candidateReparseRejected = $true }
    if (-not $candidateReparseRejected -or
        -not (Test-Path -LiteralPath $candidateReparseDirectory)) {
        throw 'Un candidato reparse debe rechazarse sin ejecutar borrado.'
    }
    [IO.Directory]::Delete($candidateReparseDirectory, $true)

    Start-Sleep -Milliseconds 2
    $unsafeQuarantineRun = New-PerfBaselineArtifactDirectory -Contract $contract
    [IO.File]::WriteAllText((Join-Path $unsafeQuarantineRun 'sentinel.txt'), 'keep')
    $unsafeQuarantineRejected = $false
    try {
        Move-PerfArtifactDirectoryToQuarantine `
            -ArtifactDirectory $unsafeQuarantineRun `
            -PathInspector {
                param($Component)
                $isQuarantine = (Split-Path -Leaf $Component) -ceq 'quarantine'
                [pscustomobject]@{
                    Exists = $true
                    IsContainer = $true
                    Attributes = if ($isQuarantine) {
                        [IO.FileAttributes]::Directory -bor [IO.FileAttributes]::ReparsePoint
                    } else { [IO.FileAttributes]::Directory }
                    FullName = $Component
                }
            }
    }
    catch { $unsafeQuarantineRejected = $true }
    if (-not $unsafeQuarantineRejected -or -not (Test-Path -LiteralPath $unsafeQuarantineRun)) {
        throw 'Una cuarentena insegura debe rechazarse antes de mover el candidato.'
    }
    [IO.Directory]::Delete($unsafeQuarantineRun, $true)

    $junctionTarget = Join-Path $artifactTestDirectory 'junction-target'
    $junctionPath = Join-Path $artifactTestDirectory 'junction-real'
    New-Item -ItemType Directory -Force -Path $junctionTarget | Out-Null
    $junctionCreated = $false
    try {
        New-Item -ItemType Junction -Path $junctionPath -Target $junctionTarget `
            -ErrorAction Stop | Out-Null
        $junctionCreated = $true
        $realJunctionRejected = $false
        try {
            Assert-PerfPathComponentsSafe `
                -AllowedRoot $artifactTestDirectory `
                -TargetPath (Join-Path $junctionPath 'child')
        }
        catch { $realJunctionRejected = $true }
        if (-not $realJunctionRejected) {
            throw 'Un junction real debe rechazarse cuando Windows permite crearlo.'
        }
    }
    finally {
        if ($junctionCreated -and (Test-Path -LiteralPath $junctionPath)) {
            [IO.Directory]::Delete($junctionPath, $false)
        }
        if (Test-Path -LiteralPath $junctionTarget) {
            [IO.Directory]::Delete($junctionTarget, $true)
        }
    }

    Start-Sleep -Milliseconds 2
    $failedRunDirectory = New-PerfBaselineArtifactDirectory -Contract $contract
    [IO.File]::WriteAllText(
        (Join-Path $failedRunDirectory 'unsafe.log'),
        "unsafe=$syntheticSecret",
        [Text.UTF8Encoding]::new($false)
    )
    $failClosedTriggered = $false
    try {
        Protect-PerfFailedRunArtifacts `
            -ArtifactDirectory $failedRunDirectory `
            -AdditionalSensitiveValues @($syntheticSecret) `
            -Protector { param($Directory, $Values) throw 'synthetic sanitizer failure' }
    }
    catch {
        $failClosedTriggered = $true
        if ($_.Exception.Message.Contains($syntheticSecret) -or
            $_.Exception.Message.Contains('synthetic sanitizer failure')) {
            throw 'El error fail-closed no debe revelar el secreto ni la causa interna.'
        }
    }
    if (-not $failClosedTriggered -or (Test-Path -LiteralPath $failedRunDirectory)) {
        throw 'Una sanitización fallida debe eliminar el directorio autorizado y fallar cerrado.'
    }
    $quarantinedFailedRun = Join-Path $PerfArtifactsDirectory (
        'quarantine\' + (Split-Path -Leaf $failedRunDirectory)
    )
    if (Test-Path -LiteralPath $quarantinedFailedRun -PathType Container) {
        Assert-PerfPathComponentsSafe -AllowedRoot $PerfArtifactsDirectory `
            -TargetPath $quarantinedFailedRun
        Assert-PerfRunMarker -RunDirectory $quarantinedFailedRun
        [IO.Directory]::Delete($quarantinedFailedRun, $true)
    }
}
finally {
    if ($null -ne $lock) { $lock.Dispose() }
    $env:PERF_PASSWORD = $previousArtifactPassword
    if (Test-Path -LiteralPath $artifactTestDirectory) {
        Remove-Item -LiteralPath $artifactTestDirectory -Recurse -Force
    }
}

Write-Host 'PASS: helpers puros del baseline conservan sus contratos.' -ForegroundColor Green
