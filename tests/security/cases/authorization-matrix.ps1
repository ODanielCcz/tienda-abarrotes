param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$api = 'http://127.0.0.1:58081'
$envFile = Join-Path $env:LOCALAPPDATA 'TiendaAbarrotes\performance\perf.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Falta perf.env; inicia primero tienda-perf.'
}
$services = @(docker ps --filter 'label=com.docker.compose.project=tienda-perf' --format '{{.Names}}')
if ($LASTEXITCODE -ne 0 -or -not ($services -contains 'tienda-perf-backend-1')) {
    throw 'No se encontró el backend del proyecto Docker tienda-perf.'
}
$values = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
if ($values.PERF_USERNAME -cne 'perf_operator') {
    throw 'El usuario de prueba debe ser perf_operator.'
}

function Invoke-Probe {
    param([string]$Method, [string]$Path, [string]$Token, [string]$Body)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $args = @{
        Uri = "$api$Path"
        Method = $Method
        Headers = $headers
        SkipHttpErrorCheck = $true
        TimeoutSec = 15
    }
    if ($Body) {
        $args['ContentType'] = 'application/json'
        $args['Body'] = $Body
    }
    Invoke-WebRequest @args
}

function Get-Token {
    param([string]$Username, [string]$Password)
    $body = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
    $response = Invoke-Probe 'POST' '/api/v1/auth/login' $null $body
    if ($response.StatusCode -ne 200) { throw "Falló login sintético: $($response.StatusCode)" }
    $token = ($response.Content | ConvertFrom-Json).data.accessToken
    if (-not $token) { throw 'La respuesta de login no incluyó JWT.' }
    $token
}

$operator = Get-Token $values.PERF_USERNAME $values.PERF_PASSWORD
$admin = Get-Token $values.BOOTSTRAP_ADMIN_USERNAME $values.BOOTSTRAP_ADMIN_PASSWORD
$anonymous = Invoke-Probe 'GET' '/api/v1/organization/branches' $null $null
$allowed = Invoke-Probe 'GET' '/api/v1/organization/branches' $operator $null
$denied = Invoke-Probe 'GET' '/api/v1/identity/users' $operator $null

$parts = $operator.Split('.')
if ($parts.Length -ne 3) { throw 'Formato JWT inesperado.' }
$signature = $parts[2]
$parts[2] = $(if ($signature[0] -eq 'A') { 'B' } else { 'A' }) + $signature.Substring(1)
$altered = Invoke-Probe 'GET' '/api/v1/organization/branches' ($parts -join '.') $null

$code = 'SEC-AUTH-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$branchBody = @{
    code = $code
    name = 'Sucursal sintética de autorización'
    timezone = 'America/Mexico_City'
    currencyCode = 'MXN'
} | ConvertTo-Json -Compress
$created = Invoke-Probe 'POST' '/api/v1/organization/branches' $admin $branchBody
if ($created.StatusCode -ne 201) { throw "No se pudo crear sucursal sintética: $($created.StatusCode)" }
$foreignId = ($created.Content | ConvertFrom-Json).data.branchId
if (-not $foreignId) { throw 'La sucursal sintética no devolvió ID.' }
$foreign = Invoke-Probe 'GET' "/api/v1/organization/branches/$foreignId" $operator $null
$owner = Invoke-Probe 'GET' "/api/v1/organization/branches/$foreignId" $admin $null
$list = Invoke-Probe 'GET' '/api/v1/organization/branches' $operator $null
$hidden = -not ($list.Content -match [regex]::Escape($foreignId))

if ($anonymous.StatusCode -ne 401 -or $allowed.StatusCode -ne 200 -or
    $denied.StatusCode -ne 403 -or $altered.StatusCode -ne 401 -or
    $foreign.StatusCode -ne 403 -or $owner.StatusCode -ne 200 -or -not $hidden) {
    throw "Falló matriz: anon=$($anonymous.StatusCode), permitido=$($allowed.StatusCode), rol=$($denied.StatusCode), JWT=$($altered.StatusCode), ajena=$($foreign.StatusCode), titular=$($owner.StatusCode), oculta=$hidden."
}
Write-Output 'Autorización: 401 anónimo, 200 permitido, 403 rol, 401 JWT alterado, 403 sucursal ajena y listado filtrado.'
