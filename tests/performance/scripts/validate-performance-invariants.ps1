[CmdletBinding()]
param(
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $repositoryRoot 'scripts\_perf-common.ps1')

Assert-PerfDockerAvailable
Import-PerfEnvironment
Assert-PerfComposeDefaultProjectName

$sqlPath = Join-Path $repositoryRoot 'tests\performance\sql\performance-invariants.sql'
$sql = Get-Content -LiteralPath $sqlPath -Raw
$arguments = @(
    'compose', '--project-name', $PerfProjectName,
    '--env-file', $PerfEnvFile,
    '-f', $PerfComposeFile,
    '-f', $PerfComposeOverlay,
    'exec', '-T', 'database',
    'psql', '-X', '-v', 'ON_ERROR_STOP=1',
    '-U', $env:POSTGRES_USER,
    '-d', $env:POSTGRES_DB,
    '-f', '-'
)

$process = [Diagnostics.Process]::new()
$process.StartInfo = [Diagnostics.ProcessStartInfo]::new('docker')
$process.StartInfo.UseShellExecute = $false
$process.StartInfo.RedirectStandardInput = $true
$process.StartInfo.RedirectStandardOutput = $true
$process.StartInfo.RedirectStandardError = $true
foreach ($argument in $arguments) {
    [void]$process.StartInfo.ArgumentList.Add($argument)
}
if (-not $process.Start()) {
    throw 'No fue posible iniciar la validación SQL de invariantes.'
}
$process.StandardInput.Write($sql)
$process.StandardInput.Close()
$stdoutTask = $process.StandardOutput.ReadToEndAsync()
$stderrTask = $process.StandardError.ReadToEndAsync()
$process.WaitForExit()
$stdout = $stdoutTask.GetAwaiter().GetResult()
$stderr = $stderrTask.GetAwaiter().GetResult()
$exitCode = $process.ExitCode
$process.Dispose()

$sanitized = ($stdout -replace '(?i)(password|secret|token)\s*[=:]\s*\S+', '$1=[REDACTED]').Trim()
if ($exitCode -ne 0) {
    # Preserve only known invariant names and counts; never persist raw DB errors.
    $violations = [regex]::Matches(($stderr + "`n" + $stdout),
        '\b(negative_stock|negative_lot_stock|payment_overflow|orphan_sale_movement|sync_sequence_gap|sync_checkpoint_mismatch|stock_drift)=\d+\b')
    $reason = if ($violations.Count -gt 0) {
        ($violations | ForEach-Object Value) -join ', '
    } else { 'INVARIANT_QUERY_FAILED' }
    $sanitized = "FAIL: exitCode=$exitCode; $reason"
}
if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
    $parent = Split-Path -Parent $OutputPath
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    [IO.File]::WriteAllText($OutputPath, $sanitized, [Text.UTF8Encoding]::new($false))
}
if ($exitCode -ne 0) {
    throw $sanitized
}
Write-Output $sanitized
