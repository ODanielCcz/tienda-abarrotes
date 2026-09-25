param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$api = 'http://127.0.0.1:58081'
$payloads = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'injection-payloads.json') -Raw | ConvertFrom-Json
$envFile = Join-Path $env:LOCALAPPDATA 'TiendaAbarrotes\performance\perf.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Falta perf.env de tienda-perf.' }
$services = @(docker ps --filter 'label=com.docker.compose.project=tienda-perf' --format '{{.Names}}')
if ($LASTEXITCODE -ne 0 -or -not ($services -contains 'tienda-perf-backend-1')) {
    throw 'No se encontró el backend del proyecto tienda-perf.'
}
$values = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
if ($values.PERF_USERNAME -cne 'perf_operator') { throw 'Usuario de prueba incorrecto.' }
$loginBody = @{ username = $values.PERF_USERNAME; password = $values.PERF_PASSWORD } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Uri "$api/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody
$token = $login.data.accessToken
if (-not $token) { throw 'No se obtuvo JWT sintético.' }

function Invoke-Probe {
    param([string]$Method, [string]$Path, [string]$Token, [string]$Body, [string]$Origin)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    if ($Origin) {
        $headers['Origin'] = $Origin
        $headers['Access-Control-Request-Method'] = 'GET'
    }
    $args = @{ Uri = "$api$Path"; Method = $Method; Headers = $headers;
        SkipHttpErrorCheck = $true; TimeoutSec = 15 }
    if ($Body) { $args['ContentType'] = 'application/json'; $args['Body'] = $Body }
    Invoke-WebRequest @args
}

$basePath = '/api/v1/catalog/products?search='
$baseline = Invoke-Probe 'GET' ($basePath + [uri]::EscapeDataString($payloads.noHitSearch)) $token $null $null
$sql = Invoke-Probe 'GET' ($basePath + [uri]::EscapeDataString($payloads.sqlSearch)) $token $null $null
$xss = Invoke-Probe 'GET' ($basePath + [uri]::EscapeDataString($payloads.xssSearch)) $token $null $null
$baseTotal = ($baseline.Content | ConvertFrom-Json).data.totalElements
$sqlTotal = ($sql.Content | ConvertFrom-Json).data.totalElements
$cors = Invoke-Probe 'OPTIONS' '/api/v1/catalog/products' $null $null 'https://evil.example'
$large = @{ username = ('A' * $payloads.oversizedBytes); password = 'x' } | ConvertTo-Json -Compress
$oversize = Invoke-Probe 'POST' '/api/v1/auth/login' $null $large $null

$nested = @{ leaf = 'x' }
for ($i = 0; $i -lt $payloads.syncDepth; $i++) { $nested = @{ nested = $nested } }
$syncBody = @{
    operationId = [guid]::NewGuid().ToString()
    deviceId = '91000000-0000-0000-0001-000000000002'
    deviceSequence = 1
    idempotencyKey = [guid]::NewGuid().ToString()
    operationType = 'CART_UPSERT'
    aggregateType = 'CART'
    payload = $nested
    clientCreatedAt = [DateTime]::UtcNow.ToString('o')
} | ConvertTo-Json -Compress -Depth 45
$deep = Invoke-Probe 'POST' '/api/v1/sync/inbox' $token $syncBody $null

function ConvertTo-Base64Url([byte[]]$bytes) {
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
$parts = $token.Split('.')
if ($parts.Length -ne 3) { throw 'Formato JWT inesperado.' }
$padded = $parts[1].Replace('-', '+').Replace('_', '/')
$padded = $padded.PadRight([int]([Math]::Ceiling($padded.Length / 4.0) * 4), '=')
$claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($padded)) | ConvertFrom-Json -AsHashtable
$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$claims['iat'] = $now - 300
$claims['exp'] = $now - 120
$encodedClaims = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes(($claims | ConvertTo-Json -Compress -Depth 20)))
$signingInput = $parts[0] + '.' + $encodedClaims
$key = [Convert]::FromBase64String($values.JWT_SECRET_BASE64)
$signature = ConvertTo-Base64Url ([Security.Cryptography.HMACSHA256]::HashData(
    $key, [Text.Encoding]::ASCII.GetBytes($signingInput)))
$expired = Invoke-Probe 'GET' '/api/v1/organization/branches' ($signingInput + '.' + $signature) $null $null

if ($baseline.StatusCode -ne 200 -or $sql.StatusCode -ne 200 -or
    $baseTotal -ne 0 -or $sqlTotal -ne 0 -or
    $xss.StatusCode -ne 200 -or $xss.Content.Contains($payloads.xssSearch) -or
    $cors.StatusCode -ne 403 -or $cors.Headers['Access-Control-Allow-Origin'] -or
    $oversize.StatusCode -ne 413 -or $deep.StatusCode -ne 400 -or
    $expired.StatusCode -ne 401) {
    throw "Falló regresión: SQL=$($sql.StatusCode)/$sqlTotal, XSS=$($xss.StatusCode), CORS=$($cors.StatusCode), tamaño=$($oversize.StatusCode), Sync=$($deep.StatusCode), JWT=$($expired.StatusCode)."
}
Write-Output 'Regresión: búsqueda SQL/XSS sin fuga ni reflejo, CORS 403, tamaño 413, Sync 400 y JWT expirado 401.'
