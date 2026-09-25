param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$configDir = Join-Path $root 'tests\security\zap'
$artifactRoot = Join-Path $env:LOCALAPPDATA 'TiendaAbarrotes\performance\artifacts'
$envFile = Join-Path $env:LOCALAPPDATA 'TiendaAbarrotes\performance\perf.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Falta perf.env de tienda-perf.' }
$values = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
if ($values.PERF_USERNAME -cne 'perf_operator' -or
    $values.BACKEND_PORT -ne '58081' -or $values.PERF_WEB_PORT -ne '54201') {
    throw 'El entorno no corresponde a los puertos y usuario sintéticos autorizados.'
}
$services = @(docker ps --filter 'label=com.docker.compose.project=tienda-perf' --format '{{.Names}}')
if ($LASTEXITCODE -ne 0 -or -not ($services -contains 'tienda-perf-backend-1') -or
    -not ($services -contains 'tienda-perf-web-performance-1')) {
    throw 'No están activos backend y web del proyecto tienda-perf.'
}
docker network inspect tienda-perf_tienda_data *> $null
if ($LASTEXITCODE -ne 0) { throw 'Falta la red exclusiva de tienda-perf.' }
foreach ($url in @('http://127.0.0.1:58081/actuator/health',
                  'http://127.0.0.1:54201/')) {
    $response = Invoke-WebRequest -Uri $url -TimeoutSec 10 -SkipHttpErrorCheck
    if ($response.StatusCode -ne 200) { throw "Destino local no saludable: $url" }
}

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
$stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$runDir = Join-Path $artifactRoot "security-$stamp"
$resolvedRoot = [IO.Path]::GetFullPath($artifactRoot).TrimEnd('\') + '\'
$resolvedRun = [IO.Path]::GetFullPath($runDir)
if (-not $resolvedRun.StartsWith($resolvedRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Directorio de artefactos fuera del área autorizada.'
}
New-Item -ItemType Directory -Path $runDir | Out-Null
foreach ($name in @('zap-baseline.json', 'zap-openapi.json')) {
    if (Test-Path -LiteralPath (Join-Path $artifactRoot $name)) {
        throw "Hay un artefacto previo sin mover: $name"
    }
}
$image = 'ghcr.io/zaproxy/zaproxy:2.17.0'
$network = 'tienda-perf_tienda_data'
$configMount = "type=bind,src=$configDir,dst=/zap/config,readonly"
$artifactMount = "type=bind,src=$artifactRoot,dst=/zap/out"
$apiArtifactMount = "type=bind,src=$artifactRoot,dst=/zap/wrk"

& docker run --rm --network $network --mount $configMount --mount $artifactMount $image zap.sh -cmd -autorun /zap/config/automation-baseline.yaml
if ($LASTEXITCODE -ne 0) { throw 'Falló el baseline pasivo ZAP.' }
Move-Item -LiteralPath (Join-Path $artifactRoot 'zap-baseline.json') -Destination $runDir

& docker run --rm --network $network --mount $apiArtifactMount $image zap-api-scan.py -t http://backend:8080/v3/api-docs -f openapi -S -I -J zap-openapi.json
if ($LASTEXITCODE -ne 0) { throw 'Falló el escaneo pasivo OpenAPI.' }
Move-Item -LiteralPath (Join-Path $artifactRoot 'zap-openapi.json') -Destination $runDir

& (Join-Path $root 'tests\security\cases\authorization-matrix.ps1')
if (-not $?) { throw 'Falló la matriz de autorización.' }
& (Join-Path $root 'tests\security\cases\security-regression.ps1')
if (-not $?) { throw 'Fallaron los casos dirigidos.' }
Write-Output "Artefactos ZAP pasivos: $runDir"
Write-Output 'El plan ZAP autenticado se ejecuta por separado con JWT sintético y autorización expresa.'
