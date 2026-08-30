[CmdletBinding()]
param(
    [string]$FixtureRoot = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'
$validationErrors = [System.Collections.Generic.List[string]]::new()
$expectedPermissionCodes = @()

function Add-ValidationError {
    param([Parameter(Mandatory = $true)][string]$Message)
    $validationErrors.Add($Message)
}

function Assert-MinimumCount {
    param(
        [Parameter(Mandatory = $true)][pscustomobject]$Counts,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Minimum
    )

    if ($null -eq $Counts.PSObject.Properties[$Name]) {
        Add-ValidationError "El contrato no define counts.$Name."
        return
    }

    $value = [int]$Counts.$Name
    if ($value -lt $Minimum) {
        Add-ValidationError "counts.$Name debe ser al menos $Minimum y vale $value."
    }
}

$contractPath = Join-Path $FixtureRoot 'fixture-contract.json'
$sqlPath = Join-Path $FixtureRoot 'performance-fixtures.sql'
$loaderPath = Join-Path $FixtureRoot 'load-performance-fixtures.ps1'
$repositoryRoot = (Resolve-Path (Join-Path $FixtureRoot '..\..\..')).Path
$environmentUpPath = Join-Path $repositoryRoot 'scripts\perf-environment-up.ps1'

foreach ($requiredPath in @($contractPath, $sqlPath, $loaderPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        Add-ValidationError "Falta el archivo requerido: $requiredPath"
    }
}

if (Test-Path -LiteralPath $contractPath -PathType Leaf) {
    try {
        $contract = Get-Content -Raw -LiteralPath $contractPath | ConvertFrom-Json
    }
    catch {
        Add-ValidationError "fixture-contract.json no es JSON válido: $($_.Exception.Message)"
    }

    if ($null -ne $contract) {
        if ([int]$contract.schemaVersion -ne 1) {
            Add-ValidationError 'schemaVersion debe ser 1.'
        }

        $maxVirtualUsers = [int]$contract.maxVirtualUsers
        if ($maxVirtualUsers -lt 150) {
            Add-ValidationError "maxVirtualUsers debe cubrir al menos 150 VU y vale $maxVirtualUsers."
        }

        if (@($contract.warehouses).Count -ne 2) {
            Add-ValidationError 'El contrato debe definir exactamente dos almacenes.'
        }

        if ($contract.generalPriceListCode -ne 'GENERAL') {
            Add-ValidationError 'La lista de precios del contrato debe ser GENERAL.'
        }

        if ($contract.restrictedUser.username -ne 'perf_operator') {
            Add-ValidationError 'El usuario restringido debe ser perf_operator.'
        }

        $permissionProperty = $contract.restrictedUser.PSObject.Properties['permissionCodes']
        if ($null -eq $permissionProperty) {
            $expectedPermissionCodes = @()
            Add-ValidationError 'El contrato debe declarar la allowlist exacta restrictedUser.permissionCodes.'
        }
        else {
            $expectedPermissionCodes = @(
                $contract.restrictedUser.permissionCodes |
                    Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }
            )
            if ($expectedPermissionCodes.Count -eq 0) {
                Add-ValidationError 'restrictedUser.permissionCodes no puede estar vacío.'
            }
            elseif (@($expectedPermissionCodes | Sort-Object -Unique).Count -ne $expectedPermissionCodes.Count) {
                Add-ValidationError 'restrictedUser.permissionCodes no puede contener duplicados.'
            }
        }

        Assert-MinimumCount -Counts $contract.counts -Name 'nonLotPresentations' -Minimum $maxVirtualUsers
        Assert-MinimumCount -Counts $contract.counts -Name 'lotPresentations' -Minimum $maxVirtualUsers
        Assert-MinimumCount -Counts $contract.counts -Name 'lotsPerLotPresentation' -Minimum 2
        Assert-MinimumCount -Counts $contract.counts -Name 'customers' -Minimum $maxVirtualUsers
        Assert-MinimumCount -Counts $contract.counts -Name 'suppliers' -Minimum 1
        Assert-MinimumCount -Counts $contract.counts -Name 'mobileDevices' -Minimum $maxVirtualUsers
        Assert-MinimumCount -Counts $contract.counts -Name 'cashRegisters' -Minimum 1

        $knownIds = @(
            $contract.branch.id,
            $contract.restrictedUser.id,
            $contract.restrictedUser.roleId,
            $contract.catalog.unitId,
            $contract.catalog.categoryId,
            $contract.catalog.brandId,
            $contract.catalog.taxId
        ) + @($contract.warehouses | ForEach-Object { $_.id })

        $parsedIds = [System.Collections.Generic.List[guid]]::new()
        foreach ($knownId in $knownIds) {
            $parsedId = [guid]::Empty
            if (-not [guid]::TryParse([string]$knownId, [ref]$parsedId)) {
                Add-ValidationError "El identificador '$knownId' no es UUID válido."
            }
            else {
                $parsedIds.Add($parsedId)
            }
        }
        if (@($parsedIds | Select-Object -Unique).Count -ne $parsedIds.Count) {
            Add-ValidationError 'Los UUID estáticos del contrato deben ser únicos.'
        }

        $contractText = Get-Content -Raw -LiteralPath $contractPath
        if ($contractText -match '(?i)"(password|secret|token)"\s*:') {
            Add-ValidationError 'El contrato no debe contener contraseñas, secretos ni tokens.'
        }
    }
}

if (Test-Path -LiteralPath $sqlPath -PathType Leaf) {
    $sql = Get-Content -Raw -LiteralPath $sqlPath
    if ($sql -match '(?im)^\s*(DROP|TRUNCATE|DELETE)\b') {
        Add-ValidationError 'El fixture no puede ejecutar DROP, TRUNCATE ni DELETE.'
    }

    foreach ($requiredTable in @(
        'organization.branches',
        'organization.warehouses',
        'organization.devices',
        'organization.cash_registers',
        'iam.users',
        'iam.roles',
        'iam.user_roles',
        'iam.role_permissions',
        'iam.user_branch_access',
        'catalog.price_lists',
        'catalog.products',
        'catalog.product_presentations',
        'catalog.prices',
        'purchasing.suppliers',
        'sales.customers',
        'inventory.lots',
        'inventory.stock_balances',
        'inventory.lot_balances',
        'sync.device_user_bindings'
    )) {
        $escapedTable = [regex]::Escape($requiredTable)
        if ($sql -notmatch "(?is)INSERT\s+INTO\s+$escapedTable\b") {
            Add-ValidationError "El fixture no carga la tabla requerida $requiredTable."
        }
    }

    foreach ($requiredVariable in @(
        'max_virtual_users',
        'non_lot_presentations',
        'lot_presentations',
        'lots_per_lot_presentation',
        'customer_count',
        'supplier_count',
        'mobile_device_count',
        'cash_register_count'
    )) {
        $variablePattern = ":'?$([regex]::Escape($requiredVariable))'?"
        if ($sql -notmatch $variablePattern) {
            Add-ValidationError "El SQL no consume la variable de contrato $requiredVariable."
        }
    }

    if ($sql -notmatch "(?is)crypt\s*\(\s*:'perf_user_password'") {
        Add-ValidationError 'El password del usuario debe recibirse fuera del repositorio y cifrarse con crypt().'
    }
    if ($sql -notmatch '(?is)ON\s+CONFLICT') {
        Add-ValidationError 'El fixture debe ser idempotente mediante ON CONFLICT.'
    }
    if ($sql -notmatch '(?is)DO\s+\$fixture_assertions\$') {
        Add-ValidationError 'El fixture debe incluir aserciones transaccionales de cardinalidad e integridad.'
    }

    if ($sql -match '(?is)ON\s+CONFLICT\s*\(stock_balance_id\)\s+DO\s+UPDATE' -or
        $sql -match '(?is)ON\s+CONFLICT\s*\(lot_balance_id\)\s+DO\s+UPDATE') {
        Add-ValidationError 'El replay del fixture no puede restablecer balances de stock o lote existentes.'
    }

    $stockBalanceNoOps = [regex]::Matches(
        $sql,
        '(?is)ON\s+CONFLICT\s*\(stock_balance_id\)\s+DO\s+NOTHING'
    ).Count
    $lotBalanceNoOps = [regex]::Matches(
        $sql,
        '(?is)ON\s+CONFLICT\s*\(lot_balance_id\)\s+DO\s+NOTHING'
    ).Count
    if ($stockBalanceNoOps -ne 2 -or $lotBalanceNoOps -ne 1) {
        Add-ValidationError 'Los dos stock_balances y el lot_balance deben usar replay no destructivo DO NOTHING.'
    }

    foreach ($requiredScopeAssertion in @(
        'PERF_EXACT_ROLE_SCOPE_ASSERTION',
        'PERF_EXACT_BRANCH_SCOPE_ASSERTION',
        'PERF_EXACT_PERMISSION_SCOPE_ASSERTION'
    )) {
        if ($sql -notmatch [regex]::Escape($requiredScopeAssertion)) {
            Add-ValidationError "Falta la aserción transaccional $requiredScopeAssertion."
        }
    }

    function Get-PermissionBlockCodes {
        param(
            [Parameter(Mandatory = $true)][string]$Source,
            [Parameter(Mandatory = $true)][string]$BlockName
        )

        $blockPattern = "(?is)-- $($BlockName)_BEGIN(?<body>.*?)-- $($BlockName)_END"
        $blockMatch = [regex]::Match($Source, $blockPattern)
        if (-not $blockMatch.Success) {
            Add-ValidationError "Falta el bloque delimitado $BlockName."
            return @()
        }
        return @(
            [regex]::Matches($blockMatch.Groups['body'].Value, "'(?<code>[A-Z][A-Z0-9_]+)'") |
                ForEach-Object { $_.Groups['code'].Value } |
                Sort-Object -Unique
        )
    }

    if ($expectedPermissionCodes.Count -gt 0) {
        $contractPermissions = @($expectedPermissionCodes | Sort-Object -Unique)
        $insertPermissions = @(Get-PermissionBlockCodes -Source $sql -BlockName 'PERF_PERMISSION_INSERT_ALLOWLIST')
        $assertPermissions = @(Get-PermissionBlockCodes -Source $sql -BlockName 'PERF_PERMISSION_ASSERT_ALLOWLIST')

        if (Compare-Object $contractPermissions $insertPermissions) {
            Add-ValidationError 'La allowlist insertada no coincide exactamente con fixture-contract.json.'
        }
        if (Compare-Object $contractPermissions $assertPermissions) {
            Add-ValidationError 'La allowlist verificada en runtime no coincide exactamente con fixture-contract.json.'
        }
    }
}

if (Test-Path -LiteralPath $loaderPath -PathType Leaf) {
    $tokens = $null
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $loaderPath,
        [ref]$tokens,
        [ref]$parseErrors
    )
    foreach ($parseError in @($parseErrors)) {
        Add-ValidationError "PowerShell inválido en el loader: $($parseError.Message)"
    }
}

if (-not (Test-Path -LiteralPath $environmentUpPath -PathType Leaf)) {
    Add-ValidationError "Falta el script de ambiente: $environmentUpPath"
}
else {
    $environmentUpSource = Get-Content -Raw -LiteralPath $environmentUpPath
    $seedOffset = $environmentUpSource.IndexOf("'db-seed'", [StringComparison]::Ordinal)
    $fixtureOffset = $environmentUpSource.IndexOf(
        "tests\performance\fixtures\load-performance-fixtures.ps1",
        [StringComparison]::Ordinal
    )
    $backendOffset = $environmentUpSource.IndexOf("@('backend', 'web-performance')", [StringComparison]::Ordinal)

    if ($seedOffset -lt 0 -or $fixtureOffset -lt 0 -or $backendOffset -lt 0) {
        Add-ValidationError 'El arranque debe declarar seed, loader de fixtures y backend/web.'
    }
    elseif (-not ($seedOffset -lt $fixtureOffset -and $fixtureOffset -lt $backendOffset)) {
        Add-ValidationError 'El orden obligatorio es db-seed, fixtures de rendimiento y después backend/web.'
    }
}

if ($validationErrors.Count -gt 0) {
    Write-Error ("Contrato de fixtures inválido:`n - " + ($validationErrors -join "`n - "))
    exit 1
}

Write-Host 'GREEN: fixtures deterministas de rendimiento cumplen el contrato estático.' -ForegroundColor Green
exit 0
