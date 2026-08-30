param(
    [switch]$SkipBuild,
    [switch]$ReuseExisting
)

. (Join-Path $PSScriptRoot '_perf-common.ps1')

Assert-PerfDockerAvailable
New-PerfEnvironmentFile
Import-PerfEnvironment
New-Item -ItemType Directory -Force -Path $PerfArtifactsDirectory | Out-Null

if (-not $ReuseExisting) {
    Invoke-PerfCompose -Arguments @('--profile', 'distributed-security', '--profile', 'performance', 'down', '-v', '--remove-orphans')
    Assert-PerfResourcesAbsent
    Assert-PerfPortsAvailable
}

if (-not $SkipBuild) {
    Push-Location (Join-Path $PerfProjectRoot 'apps\backend')
    try {
        & .\gradlew.bat bootJar --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Falló bootJar.' }
    } finally {
        Pop-Location
    }

    Push-Location (Join-Path $PerfProjectRoot 'apps\web')
    try {
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Falló el build Angular.' }
    } finally {
        Pop-Location
    }
}

Invoke-PerfCompose -Arguments @('--profile', 'distributed-security', '--profile', 'performance', 'up', '-d', 'database', 'redis')
Invoke-PerfCompose -Arguments @('--profile', 'migrate', 'run', '--rm', 'flyway', 'migrate')
Invoke-PerfCompose -Arguments @('--profile', 'seed', 'run', '--rm', 'db-seed')
$upArguments = @('--profile', 'distributed-security', '--profile', 'performance', 'up', '-d')
if (-not $SkipBuild) {
    $upArguments += '--build'
}
$upArguments += @('backend', 'web-performance')
Invoke-PerfCompose -Arguments $upArguments
Wait-PerfBackendHealthy
Invoke-PerfCompose -Arguments @('ps')

Write-Output 'Ambiente aislado listo.'
Write-Output 'Backend: http://localhost:58081'
Write-Output 'Angular: http://localhost:54201'
Write-Output "Secretos efímeros fuera de OneDrive: $PerfEnvFile"
Write-Output "Artefactos crudos fuera de OneDrive: $PerfArtifactsDirectory"
