$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $ProjectRoot '.env'

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "No existe $EnvFile. Copia .env.example como .env y configura claves locales."
}

$LocalEnvironment = @{}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $LocalEnvironment[$matches[1].Trim()] = $matches[2].Trim()
    }
}
$DatabaseUser = if ($LocalEnvironment['POSTGRES_USER']) { $LocalEnvironment['POSTGRES_USER'] } else { 'tienda_owner' }
$DatabaseName = if ($LocalEnvironment['POSTGRES_DB']) { $LocalEnvironment['POSTGRES_DB'] } else { 'tienda_abarrotes' }

function Invoke-Compose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$ComposeArguments
    )
    & docker compose --project-directory $ProjectRoot -f (Join-Path $ProjectRoot 'compose.yaml') @ComposeArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose terminó con código $LASTEXITCODE."
    }
}

