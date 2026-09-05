$ErrorActionPreference = 'Stop'

$PerfProjectName = 'tienda-perf'
$PerfProjectRoot = Split-Path -Parent $PSScriptRoot
$PerfLocalData = [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
if ([string]::IsNullOrWhiteSpace($PerfLocalData)) {
    throw 'No fue posible resolver LOCALAPPDATA para guardar secretos de rendimiento.'
}
$PerfDirectory = Join-Path $PerfLocalData 'TiendaAbarrotes\performance'
$PerfEnvFile = Join-Path $PerfDirectory 'perf.env'
$PerfArtifactsDirectory = Join-Path $PerfDirectory 'artifacts'
$PerfComposeFile = Join-Path $PerfProjectRoot 'compose.yaml'
$PerfComposeOverlay = Join-Path $PerfProjectRoot 'tests\performance\compose.performance.yaml'

function Assert-PerfDockerAvailable {
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Desktop no está disponible. Inícialo antes de continuar.'
    }
}

function New-PerfSecret {
    param(
        [int]$Bytes = 32,
        [switch]$Base64
    )

    $value = [Security.Cryptography.RandomNumberGenerator]::GetBytes($Bytes)
    if ($Base64) {
        return [Convert]::ToBase64String($value)
    }
    return [Convert]::ToHexString($value).ToLowerInvariant()
}

function New-PerfEnvironmentFile {
    if (Test-Path -LiteralPath $PerfEnvFile) {
        return
    }

    New-Item -ItemType Directory -Force -Path $PerfDirectory | Out-Null
    New-Item -ItemType Directory -Force -Path $PerfArtifactsDirectory | Out-Null
    $adminPassword = "Perf-$(New-PerfSecret -Bytes 18)!"
    $perfOperatorPassword = "PerfOperator-$(New-PerfSecret -Bytes 18)!"
    $composeArtifactsPath = $PerfArtifactsDirectory.Replace('\', '/')
    $lines = @(
        'POSTGRES_DB=tienda_performance',
        'POSTGRES_USER=tienda_owner',
        "POSTGRES_PASSWORD=$(New-PerfSecret -Bytes 32)",
        'POSTGRES_PORT=55434',
        'BACKEND_PORT=58081',
        'PERF_WEB_PORT=54201',
        "JWT_SECRET_BASE64=$(New-PerfSecret -Bytes 48 -Base64)",
        "RATE_LIMIT_KEY_SECRET_BASE64=$(New-PerfSecret -Bytes 32 -Base64)",
        "REDIS_PASSWORD=$(New-PerfSecret -Bytes 32)",
        "PGADMIN_DEFAULT_PASSWORD=$(New-PerfSecret -Bytes 32)",
        'REDIS_HOST=redis',
        'REDIS_PORT=6379',
        'LOGIN_RATE_LIMIT_PROVIDER=redis',
        'LOGIN_RATE_LIMIT_UNAVAILABLE_RETRY_AFTER=PT5S',
        'BOOTSTRAP_ADMIN_ENABLED=true',
        'BOOTSTRAP_ADMIN_USERNAME=perf_admin',
        "BOOTSTRAP_ADMIN_PASSWORD=$adminPassword",
        'BOOTSTRAP_ADMIN_DISPLAY_NAME=Administrador de rendimiento',
        'JWT_ISSUER=tienda-abarrotes-performance',
        'JWT_ACCESS_TOKEN_TTL=PT4H',
        'CORS_ALLOWED_ORIGINS=http://localhost:54201',
        'API_MAX_JSON_PAYLOAD_BYTES=1048576',
        'DB_POOL_MAX_SIZE=20',
        'DB_POOL_MIN_IDLE=5',
        'E2E_USERNAME=perf_admin',
        "E2E_PASSWORD=$adminPassword",
        'PERF_USERNAME=perf_operator',
        "PERF_FIXTURE_USER_PASSWORD=$perfOperatorPassword",
        "PERF_PASSWORD=$perfOperatorPassword",
        "PERF_ARTIFACTS_DIR=$composeArtifactsPath"
    )
    [IO.File]::WriteAllLines($PerfEnvFile, $lines, [Text.UTF8Encoding]::new($false))
}

function Assert-PerfPortsAvailable {
    $ports = @(
        [int]$env:POSTGRES_PORT,
        [int]$env:BACKEND_PORT,
        [int]$env:PERF_WEB_PORT
    )
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    $busy = $ports | Where-Object { $_ -in $listeners.Port }
    if ($busy.Count -gt 0) {
        throw "Los puertos de rendimiento ya están ocupados: $($busy -join ', ')."
    }
}

function Import-PerfEnvironment {
    param([switch]$TeardownOnly)

    if (-not (Test-Path -LiteralPath $PerfEnvFile)) {
        throw "No existe $PerfEnvFile. Ejecuta primero scripts\perf-environment-up.ps1."
    }

    $loadedNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    Get-Content -LiteralPath $PerfEnvFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
            $name = $matches[1].Trim()
            [Environment]::SetEnvironmentVariable($name, $matches[2].Trim(), 'Process')
            [void]$loadedNames.Add($name)
        }
    }
    $script:PerfEnvironmentLoadedNames = @($loadedNames)
    if ($TeardownOnly) {
        $placeholder = "teardown-$(New-PerfSecret -Bytes 16)"
        $env:PERF_USERNAME = $placeholder
        $env:PERF_FIXTURE_USER_PASSWORD = $placeholder
        $env:PERF_PASSWORD = $placeholder
        return
    }
    foreach ($requiredName in @('PERF_USERNAME', 'PERF_FIXTURE_USER_PASSWORD', 'PERF_PASSWORD')) {
        if (-not $loadedNames.Contains($requiredName)) {
            throw "$requiredName debe estar presente en $PerfEnvFile; las variables heredadas del proceso no son válidas."
        }
    }
    if ($env:PERF_USERNAME -cne 'perf_operator') {
        throw 'PERF_USERNAME debe ser exactamente perf_operator para las pruebas restringidas.'
    }
    if ([string]::IsNullOrWhiteSpace($env:PERF_FIXTURE_USER_PASSWORD)) {
        throw 'PERF_FIXTURE_USER_PASSWORD es obligatorio; recrea el ambiente de rendimiento si falta.'
    }
    if ([string]::IsNullOrWhiteSpace($env:PERF_PASSWORD)) {
        throw 'PERF_PASSWORD es obligatorio y debe coincidir con la contraseña del fixture.'
    }
    if ($env:PERF_PASSWORD -cne $env:PERF_FIXTURE_USER_PASSWORD) {
        throw 'PERF_PASSWORD debe coincidir con PERF_FIXTURE_USER_PASSWORD.'
    }
    foreach ($privilegedPassword in @($env:E2E_PASSWORD, $env:BOOTSTRAP_ADMIN_PASSWORD)) {
        if (-not [string]::IsNullOrWhiteSpace($privilegedPassword) -and (
            $env:PERF_FIXTURE_USER_PASSWORD -ceq $privilegedPassword -or
            $env:PERF_PASSWORD -ceq $privilegedPassword
        )) {
            throw 'Las credenciales de rendimiento no pueden reutilizar una contraseña privilegiada.'
        }
    }
}

function Invoke-PerfCompose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & docker compose `
        --project-name $PerfProjectName `
        --env-file $PerfEnvFile `
        -f $PerfComposeFile `
        -f $PerfComposeOverlay `
        @Arguments
    if ($LASTEXITCODE -ne 0) {
        $failure = [InvalidOperationException]::new("Docker Compose terminó con código $LASTEXITCODE.")
        $failure.Data['NativeExitCode'] = [int]$LASTEXITCODE
        throw $failure
    }
}

function Assert-PerfComposeDefaultProjectName {
    $output = & docker compose `
        --env-file $PerfEnvFile `
        -f $PerfComposeFile `
        -f $PerfComposeOverlay `
        config --format json
    if ($LASTEXITCODE -ne 0) {
        throw "No fue posible evaluar la configuración Compose de rendimiento (código $LASTEXITCODE)."
    }
    $configuration = (($output | Out-String) | ConvertFrom-Json)
    if ($configuration.name -ne $PerfProjectName) {
        throw "El nombre Compose efectivo es '$($configuration.name)', se esperaba '$PerfProjectName'."
    }
}

function Get-PerfComposeOutput {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = & docker compose `
        --project-name $PerfProjectName `
        --env-file $PerfEnvFile `
        -f $PerfComposeFile `
        -f $PerfComposeOverlay `
        @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose terminó con código $LASTEXITCODE."
    }
    return (($output | Out-String).Trim())
}

function Get-PerfResourceIds {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('container', 'network', 'volume')]
        [string]$ResourceType
    )

    $arguments = switch ($ResourceType) {
        'container' { @('ps', '-aq') }
        'network' { @('network', 'ls', '-q') }
        'volume' { @('volume', 'ls', '-q') }
    }
    return @(& docker @arguments --filter "label=com.docker.compose.project=$PerfProjectName") |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
}

function Assert-PerfResourcesAbsent {
    $remaining = @()
    foreach ($resourceType in @('container', 'network', 'volume')) {
        $ids = @(Get-PerfResourceIds -ResourceType $resourceType)
        if ($ids.Count -gt 0) {
            $remaining += "$resourceType=$($ids -join ',')"
        }
    }
    if ($remaining.Count -gt 0) {
        throw "Quedaron recursos del proyecto ${PerfProjectName}: $($remaining -join '; ')."
    }
}

function Wait-PerfBackendHealthy {
    param([int]$Attempts = 36)

    $containerId = Get-PerfComposeOutput -Arguments @('ps', '-q', 'backend')
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        throw 'No se encontró el contenedor backend del ambiente de rendimiento.'
    }
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $status = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId 2>$null
        if ($status -eq 'healthy') {
            return
        }
        if ($status -eq 'unhealthy') {
            throw 'El backend de rendimiento quedó unhealthy.'
        }
        Start-Sleep -Seconds 5
    }
    throw 'El backend de rendimiento no quedó healthy dentro del tiempo esperado.'
}
