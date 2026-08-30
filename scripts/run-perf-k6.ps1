[CmdletBinding()]
param(
    [ValidateSet('run', 'inspect')]
    [string]$Command = 'run',
    [string]$Script = 'backend-smoke.js',
    [string]$SmokeRunId,
    [switch]$ExpectInvalidUrl,
    [switch]$ExpectAbortMidway,
    [switch]$ValidateComposeProject
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_perf-common.ps1')

Assert-PerfDockerAvailable
Import-PerfEnvironment
Assert-PerfComposeDefaultProjectName

if ($ValidateComposeProject) {
    Write-Output "El nombre Compose efectivo sin --project-name es '$PerfProjectName'."
    return
}

$scriptPath = Join-Path $PerfProjectRoot "tests\performance\k6\$Script"
if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "No existe el script k6 solicitado: $scriptPath"
}

$composeArguments = @('--profile', 'performance', 'run', '--rm', '--no-deps')
if (-not [string]::IsNullOrWhiteSpace($SmokeRunId)) {
    if ($SmokeRunId -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$') {
        throw 'SmokeRunId debe ser un UUID válido.'
    }
    $composeArguments += @('--env', "SMOKE_RUN_ID=$SmokeRunId")
}
if ($ExpectInvalidUrl) {
    $composeArguments += @('--env', 'EXPECT_INVALID_URL=true')
}
if ($ExpectAbortMidway) {
    $composeArguments += @('--env', 'EXPECT_ABORT_MIDWAY=true')
}
$composeArguments += @('k6', $Command, $Script)
Invoke-PerfCompose -Arguments $composeArguments
