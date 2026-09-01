[CmdletBinding()]
param(
    [ValidateSet('run', 'inspect')]
    [string]$Command = 'run',
    [string]$Script = 'backend-smoke.js',
    [string]$SmokeRunId,
    [string]$RunId,
    [switch]$ExpectInvalidUrl,
    [switch]$ExpectAbortMidway,
    [switch]$QuickLoad,
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
if (-not [string]::IsNullOrWhiteSpace($SmokeRunId) -and -not [string]::IsNullOrWhiteSpace($RunId)) {
    throw 'Usa SmokeRunId o RunId, no ambos.'
}
if (-not [string]::IsNullOrWhiteSpace($SmokeRunId)) {
    if ($SmokeRunId -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$') {
        throw 'SmokeRunId debe ser un UUID válido.'
    }
    $composeArguments += @('--env', "SMOKE_RUN_ID=$SmokeRunId")
}
if (-not [string]::IsNullOrWhiteSpace($RunId)) {
    if ($RunId -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$') {
        throw 'RunId debe ser un UUID válido.'
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $PerfArtifactsDirectory 'load') | Out-Null
    $composeArguments += @('--env', "PERF_RUN_ID=$RunId")
}
if ($ExpectInvalidUrl) {
    $composeArguments += @('--env', 'EXPECT_INVALID_URL=true')
}
if ($ExpectAbortMidway) {
    $composeArguments += @('--env', 'EXPECT_ABORT_MIDWAY=true')
}
if ($QuickLoad) {
    if ($Script -cne 'backend-load.js') {
        throw 'QuickLoad solo puede usarse con backend-load.js.'
    }
    $composeArguments += @('--env', 'PERF_LOAD_QUICK=true')
}
$composeArguments += @('k6', $Command, $Script)
Invoke-PerfCompose -Arguments $composeArguments
