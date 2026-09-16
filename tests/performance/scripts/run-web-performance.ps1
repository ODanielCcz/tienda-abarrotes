[CmdletBinding()]
param(
    [ValidateSet('standalone', 'combined', 'lighthouse')][string]$Mode = 'standalone',
    [switch]$Quick
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $root 'scripts\_perf-common.ps1')
Assert-PerfDockerAvailable
Import-PerfEnvironment
& (Join-Path $root 'scripts\perf-environment-up.ps1') -SkipBuild
& (Join-Path $root 'scripts\run-perf-baseline.ps1') -ValidateOnly
$id = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + $Mode
$directory = Join-Path $PerfArtifactsDirectory "web\$id"
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$browserJob = $null
$loadJob = $null
$loadId = [Guid]::NewGuid().ToString()
$ownsControlEndpoint = $false
$samples = [Collections.Generic.List[object]]::new()
$result = [ordered]@{ runId = $id; mode = $Mode; quick = [bool]$Quick
    startedAt = [DateTimeOffset]::UtcNow.ToString('o'); completedAt = $null
    gitRevision = (& git -C $root rev-parse HEAD | Out-String).Trim()
    gitDirtyEntryCount = @(& git -C $root status --porcelain).Count
    nodeVersion = (& node --version | Out-String).Trim()
    status = 'RUNNING'; browserExitCode = $null; loadRunId = $null; loadSamples = @() }
$build = Join-Path $root 'apps\web\dist\tienda-web\browser'
if (-not (Test-Path (Join-Path $build 'index.html'))) { throw 'Falta el build de producción.' }
$result.webBuildFiles = @(Get-ChildItem -LiteralPath $build -File -Recurse | ForEach-Object {
    [pscustomobject]@{ path = [IO.Path]::GetRelativePath($build, $_.FullName)
        bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
})
try {
    if ($Mode -eq 'combined') {
        if (56565 -in [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port) {
            throw 'El puerto de control 56565 ya está ocupado; no se usará un proceso ajeno.'
        }
        $result.loadRunId = $loadId
        $loadJob = Start-Job -ArgumentList $root, $loadId, $directory -ScriptBlock {
            param($root, $id, $output)
            . (Join-Path $root 'scripts\_perf-common.ps1')
            Import-PerfEnvironment
            try {
                Invoke-PerfCompose -Arguments @('--profile', 'performance', 'run', '--rm', '--no-deps',
                    '--name', "tienda-perf-web-load-$id", '-p', '127.0.0.1:56565:6565',
                    '--env', "PERF_RUN_ID=$id",
                    'k6', 'run', '--address', '0.0.0.0:6565', 'backend-web-load.js') *>&1 |
                    Out-File (Join-Path $output 'k6-console.txt') -Encoding utf8
                [pscustomobject]@{ exitCode = 0 }
            } catch { [pscustomobject]@{ exitCode = $_.Exception.Data['NativeExitCode'] } }
        }
        $ready = $false
        $deadline = [DateTimeOffset]::UtcNow.AddSeconds(60)
        while ([DateTimeOffset]::UtcNow -lt $deadline) {
            try {
                $owner = & docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "tienda-perf-web-load-$loadId" 2>$null
                if ($LASTEXITCODE -ne 0 -or $owner -cne 'tienda-perf') { Start-Sleep -Seconds 1; continue }
                $ownsControlEndpoint = $true
                $status = Invoke-RestMethod 'http://127.0.0.1:56565/v1/status' -TimeoutSec 2
                if ($status.data.attributes.running -and $status.data.attributes.vus -eq 25) { $ready = $true; break }
            } catch { }
            Start-Sleep -Seconds 1
        }
        if (-not $ready) { throw 'No se confirmó carga activa de 25 VU.' }
    }
    $browserJob = Start-Job -ArgumentList $root, $id, $Mode, ([bool]$Quick), $directory -ScriptBlock {
        param($root, $id, $mode, $quick, $output)
        Set-Location (Join-Path $root 'apps\web')
        $env:PERF_WEB_RUN_ID = $id
        $env:PERF_WEB_MODE = $mode
        $env:PERF_WEB_QUICK = if ($quick) { 'true' } else { 'false' }
        if ($mode -eq 'lighthouse') {
            & node (Join-Path $root 'tests\performance\web\run-lighthouse.mjs') *>&1 |
                Out-File (Join-Path $output 'browser-console.txt') -Encoding utf8
        } else {
            & .\node_modules\.bin\playwright.cmd test --config playwright.performance.config.ts *>&1 |
                Out-File (Join-Path $output 'browser-console.txt') -Encoding utf8
        }
        [pscustomobject]@{ exitCode = $LASTEXITCODE }
    }
    while ($browserJob.State -in @('Running', 'NotStarted')) {
        if ($Mode -eq 'combined') {
            try {
                $status = Invoke-RestMethod 'http://127.0.0.1:56565/v1/status' -TimeoutSec 2
                $samples.Add([pscustomobject]@{ at = [DateTimeOffset]::UtcNow.ToString('o')
                    running = [bool]$status.data.attributes.running; vus = $status.data.attributes.vus })
            } catch { $samples.Add([pscustomobject]@{ at = [DateTimeOffset]::UtcNow.ToString('o'); running = $false; vus = 0 }) }
        }
        Start-Sleep -Seconds 5
    }
    $browserResult = @(Receive-Job $browserJob)[-1]
    $result.browserExitCode = $browserResult.exitCode
    $coverage = $Mode -ne 'combined' -or ($samples.Count -gt 0 -and @($samples | Where-Object { -not $_.running -or $_.vus -ne 25 }).Count -eq 0)
    $result.status = if ($browserResult.exitCode -eq 0 -and $coverage) { 'PASSED' } else { 'FINDINGS_OR_INCOMPLETE' }
} catch {
    $result.status = 'FAILED'
    throw
} finally {
    if ($null -ne $loadJob) {
        try {
            if (-not $ownsControlEndpoint) { throw 'No se verificó propiedad del control k6.' }
            Invoke-RestMethod 'http://127.0.0.1:56565/v1/status' -Method Patch -ContentType 'application/json' -TimeoutSec 5 `
                -Body '{"data":{"type":"status","id":"default","attributes":{"stopped":true}}}' | Out-Null
        } catch { $result.status = 'INCOMPLETE_LOAD_STOP' }
        Wait-Job $loadJob -Timeout 30 | Out-Null
        if ($loadJob.State -eq 'Running') {
            $result.status = 'INCOMPLETE_LOAD_STOP'
            $owner = & docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "tienda-perf-web-load-$loadId" 2>$null
            if ($LASTEXITCODE -eq 0 -and $owner -ceq 'tienda-perf') {
                & docker stop --time 10 "tienda-perf-web-load-$loadId" | Out-Null
                Wait-Job $loadJob -Timeout 15 | Out-Null
            }
        }
        else { Receive-Job $loadJob | Out-Null }
        $summary = Join-Path $PerfArtifactsDirectory "$loadId-backend-web-load-summary.json"
        if (Test-Path $summary) {
            Copy-Item -LiteralPath $summary -Destination (Join-Path $directory 'k6-summary.json')
            $metrics = (Get-Content -LiteralPath $summary -Raw | ConvertFrom-Json).metrics
            if ($null -eq $metrics.checks -or $metrics.checks.rate -ne 1 -or
                $metrics.http_reqs.count -le 0 -or $metrics.http_req_failed.rate -ne 0 -or
                $metrics.load_completion.count -le 0 -or
                $null -eq $metrics.read_p95 -or $metrics.read_p95.'p(95)' -ge 500 -or
                $null -eq $metrics.write_p95 -or $metrics.write_p95.'p(95)' -ge 900 -or
                $null -eq $metrics.business_p99 -or $metrics.business_p99.'p(99)' -ge 1500 -or
                $metrics.unexpected_5xx.count -gt 0 -or $metrics.schema_error.count -gt 0 -or
                $metrics.unexpected_functional_error.count -gt 0) {
                $result.status = 'BACKEND_LOAD_FINDINGS'
            }
        }
        else { $result.status = 'INCOMPLETE_LOAD_SUMMARY' }
        try {
            & (Join-Path $PSScriptRoot 'validate-performance-invariants.ps1') -OutputPath (Join-Path $directory 'invariants.txt')
        } catch { $result.status = 'INVARIANT_FAILED' }
    }
    $result.completedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $result.loadSamples = @($samples)
    $result | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $directory 'campaign.json') -Encoding utf8NoBOM
    if ($null -ne $browserJob) { Remove-Job $browserJob -Force }
    if ($null -ne $loadJob -and $loadJob.State -ne 'Running') { Remove-Job $loadJob -Force }
}
Write-Host "Resultado $($result.status): $directory"
if ($result.status -ne 'PASSED') { throw 'Revisar hallazgos o evidencia incompleta del navegador.' }
