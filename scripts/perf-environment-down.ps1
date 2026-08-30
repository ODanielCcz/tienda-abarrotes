param(
    [switch]$KeepCredentials
)

. (Join-Path $PSScriptRoot '_perf-common.ps1')

Assert-PerfDockerAvailable
New-PerfEnvironmentFile
Import-PerfEnvironment -TeardownOnly
Invoke-PerfCompose -Arguments @('--profile', 'distributed-security', '--profile', 'performance', 'down', '-v', '--remove-orphans', '--timeout', '30')
Assert-PerfResourcesAbsent

if (-not $KeepCredentials -and (Test-Path -LiteralPath $PerfEnvFile)) {
    Remove-Item -LiteralPath $PerfEnvFile -Force
}

Write-Output 'Ambiente tienda-perf detenido y volúmenes eliminados.'
