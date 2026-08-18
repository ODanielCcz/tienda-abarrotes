[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$MappingPath
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '_db-common.ps1')

function ConvertTo-RequiredUuid {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value,
        [Parameter(Mandatory = $true)]
        [string]$Column,
        [Parameter(Mandatory = $true)]
        [int]$RowNumber
    )

    $parsed = [guid]::Empty
    if (-not [guid]::TryParse($Value, [ref]$parsed) -or $parsed -eq [guid]::Empty) {
        throw "Fila ${RowNumber}: $Column debe contener un UUID distinto de cero."
    }
    return $parsed.ToString()
}

function Invoke-DatabaseQuery {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Query,
        [Parameter(Mandatory = $true)]
        [hashtable]$Variables
    )

    $arguments = @('exec', '-T', 'database', 'psql', '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-U', $DatabaseUser, '-d', $DatabaseName)
    foreach ($key in $Variables.Keys) {
        $arguments += @('-v', "$key=$($Variables[$key])")
    }
    $arguments += @('-c', $Query)
    Invoke-Compose -ComposeArguments $arguments
}

function ConvertTo-SqlUuidLiteral {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    # ConvertTo-RequiredUuid ya garantiza que el valor es un UUID; al insertar
    # únicamente ese formato validado, no se permite inyección en estas consultas.
    return "'$Value'::uuid"
}

$rows = @(Import-Csv -LiteralPath $MappingPath)
if ($rows.Count -eq 0) {
    throw 'El CSV no contiene asignaciones. Usa las columnas device_id,user_id.'
}

$seenDevices = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$validatedRows = for ($index = 0; $index -lt $rows.Count; $index++) {
    $rowNumber = $index + 2
    $row = $rows[$index]
    $deviceId = ConvertTo-RequiredUuid -Value $row.device_id -Column 'device_id' -RowNumber $rowNumber
    $userId = ConvertTo-RequiredUuid -Value $row.user_id -Column 'user_id' -RowNumber $rowNumber
    if (-not $seenDevices.Add($deviceId)) {
        throw "Fila ${rowNumber}: el dispositivo $deviceId aparece más de una vez."
    }
    [PSCustomObject]@{ DeviceId = $deviceId; UserId = $userId; RowNumber = $rowNumber }
}

$validationQuery = @'
WITH current_binding AS (
    SELECT binding.user_id
    FROM sync.device_user_bindings binding
    WHERE binding.device_id = :'device_id'::uuid
)
SELECT CASE
    WHEN NOT EXISTS (
        SELECT 1
        FROM organization.devices device
        JOIN organization.branches branch ON branch.branch_id = device.branch_id
        WHERE device.device_id = :'device_id'::uuid
          AND device.status = 'ACTIVE'
          AND device.device_type = 'MOBILE_EMPLOYEE'
          AND branch.status = 'ACTIVE'
    ) THEN 'DEVICE_NOT_ELIGIBLE'
    WHEN NOT EXISTS (
        SELECT 1
        FROM iam.users user_account
        WHERE user_account.user_id = :'user_id'::uuid
          AND user_account.status = 'ACTIVE'
    ) THEN 'USER_NOT_ACTIVE'
    WHEN NOT EXISTS (
        SELECT 1
        FROM organization.devices device
        WHERE device.device_id = :'device_id'::uuid
          AND (
              EXISTS (
                  SELECT 1
                  FROM iam.user_roles user_role
                  JOIN iam.roles role ON role.role_id = user_role.role_id
                  WHERE user_role.user_id = :'user_id'::uuid
                    AND role.code = 'SYSTEM_ADMIN'
                    AND role.status = 'ACTIVE'
                    AND (user_role.valid_until IS NULL OR user_role.valid_until > clock_timestamp())
              )
              OR EXISTS (
                  SELECT 1
                  FROM iam.user_branch_access access
                  WHERE access.user_id = :'user_id'::uuid
                    AND access.branch_id = device.branch_id
                    AND access.status = 'ACTIVE'
              )
          )
    ) THEN 'USER_HAS_NO_ACTIVE_BRANCH_ACCESS'
    WHEN EXISTS (
        SELECT 1 FROM current_binding WHERE user_id <> :'user_id'::uuid
    ) THEN 'DEVICE_ALREADY_BOUND_TO_ANOTHER_USER'
    ELSE 'OK'
END;
'@

$insertQuery = @'
INSERT INTO sync.device_user_bindings (device_id, user_id)
VALUES (:'device_id'::uuid, :'user_id'::uuid)
ON CONFLICT (device_id) DO NOTHING
RETURNING device_id;
'@

foreach ($mapping in $validatedRows) {
    $deviceLiteral = ConvertTo-SqlUuidLiteral -Value $mapping.DeviceId
    $userLiteral = ConvertTo-SqlUuidLiteral -Value $mapping.UserId
    $renderedValidationQuery = $validationQuery.Replace(":'device_id'::uuid", $deviceLiteral).Replace(":'user_id'::uuid", $userLiteral)
    $status = (Invoke-DatabaseQuery -Query $renderedValidationQuery -Variables @{} | Select-Object -Last 1).Trim()
    if ($status -ne 'OK') {
        throw "Fila $($mapping.RowNumber): no se puede vincular $($mapping.DeviceId) con $($mapping.UserId): $status."
    }

    $target = "device=$($mapping.DeviceId), user=$($mapping.UserId)"
    if (-not $PSCmdlet.ShouldProcess($target, 'Crear vínculo Sync de dispositivo y usuario')) {
        continue
    }

    $renderedInsertQuery = $insertQuery.Replace(":'device_id'::uuid", $deviceLiteral).Replace(":'user_id'::uuid", $userLiteral)
    $createdDeviceId = (Invoke-DatabaseQuery -Query $renderedInsertQuery -Variables @{} | Select-Object -Last 1).Trim()
    if ($createdDeviceId) {
        Write-Host "Vínculo creado: $target"
    } else {
        Write-Host "Vínculo ya existente y conservado: $target"
    }
}
