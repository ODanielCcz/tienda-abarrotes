$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $root 'scripts\_perf-common.ps1')
. (Join-Path $PSScriptRoot 'capture-performance-snapshot.ps1')

# A synthetic native command proves exit codes survive the wrapper exception.
function docker { $global:LASTEXITCODE = 99 }
try {
    Invoke-PerfCompose -Arguments @('version')
    throw 'Expected native command failure.'
} catch {
    if ($_.Exception.Data['NativeExitCode'] -ne 99) { throw }
} finally { Remove-Item Function:\docker }

$contract = [pscustomobject]@{
    prometheusMetrics = @{ critical = @() }
    database = @{ name = 'test'; owner = 'test' }
    requiredServices = @('backend')
}
$snapshot = [pscustomobject]@{
    phase = 'during'
    readiness = @{ httpStatus = 503; status = 'DOWN' }
    postgres = @{ databaseName = 'test'; databaseUser = 'test' }
    docker = @(@{ service = 'backend' })
}
Assert-PerfStressSnapshot -Snapshot $snapshot -Contract $contract
foreach ($phase in @('before', 'after')) {
    $snapshot.phase = $phase
    $rejected = $false
    try { Assert-PerfStressSnapshot -Snapshot $snapshot -Contract $contract }
    catch { $rejected = $true }
    if (-not $rejected) { throw "DOWN readiness must reject phase $phase." }
}
Write-Output 'PASS: native exit codes preserved; DOWN readiness recorded during load and rejected at boundaries.'
if (-not (Test-PerfStressEvidence -RecoverySeconds 60 -ValidDuringSamples 1 -FailedDuringSamples 0)) {
    throw 'Complete evidence at the 60 second boundary must be accepted.'
}
foreach ($case in @(
    @{ RecoverySeconds = 60.001; ValidDuringSamples = 1; FailedDuringSamples = 0 },
    @{ RecoverySeconds = $null; ValidDuringSamples = 1; FailedDuringSamples = 0 },
    @{ RecoverySeconds = 1; ValidDuringSamples = 0; FailedDuringSamples = 0 },
    @{ RecoverySeconds = 1; ValidDuringSamples = 1; FailedDuringSamples = 1 }
)) {
    if (Test-PerfStressEvidence @case) { throw 'Incomplete or late recovery evidence must fail.' }
}
Write-Output 'PASS: missing captures and recovery beyond 60 seconds cannot pass.'
