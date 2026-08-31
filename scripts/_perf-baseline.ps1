$ErrorActionPreference = 'Stop'
$script:PerfBaselineDockerTimeoutSeconds = 30
$script:PerfBaselinePsqlTimeoutSeconds = 30
$script:PerfBaselineHttpTimeoutSeconds = 15
$script:PerfBaselineLoginTimeoutSeconds = 15
$script:PerfBaselineDatabaseName = 'tienda_performance'
$script:PerfBaselineDatabaseOwner = 'tienda_owner'
$script:PerfBaselineRunNamePattern = '^\d{8}T\d{9}Z$'
$script:PerfBaselineMarkerName = '.baseline-run-marker.json'
$script:PerfBaselineMarkerKind = 'TIENDA_PERF_BASELINE_RUN_V1'

function Get-PerfBaselineContract {
    $path = Join-Path $PerfProjectRoot 'tests\performance\baseline\baseline-contract.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "No existe el contrato de baseline: $path"
    }
    return Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
}

function Get-PerfSafeEnvironment {
    param([hashtable]$Additional = @{})

    $safe = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($name in @(
        'SystemRoot', 'WINDIR', 'COMSPEC', 'TEMP', 'TMP', 'PATH', 'PATHEXT',
        'USERPROFILE', 'HOME', 'APPDATA', 'LOCALAPPDATA', 'ProgramData',
        'ProgramFiles', 'ProgramFiles(x86)', 'JAVA_HOME', 'GRADLE_USER_HOME',
        'NPM_CONFIG_CACHE', 'DOCKER_CONFIG', 'DOCKER_CONTEXT', 'DOCKER_HOST',
        'LANG', 'LC_ALL', 'CI'
    )) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        if (-not [string]::IsNullOrWhiteSpace($value)) { $safe[$name] = $value }
    }
    foreach ($entry in $Additional.GetEnumerator()) {
        if ($null -ne $entry.Value) { $safe[[string]$entry.Key] = [string]$entry.Value }
    }
    return $safe
}

function ConvertTo-PerfCmdArgument {
    param([Parameter(Mandatory = $true)][string]$Value)

    if ($Value -notmatch '[\s"&|<>^]') { return $Value }
    return '"' + $Value.Replace('"', '""') + '"'
}

function Invoke-PerfNativeProcess {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @(),
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [string]$WorkingDirectory = $PerfProjectRoot,
        [string]$StandardInput,
        [hashtable]$AdditionalEnvironment = @{},
        [string]$Stage = 'external-command'
    )

    if ($TimeoutSeconds -le 0) { throw "Timeout inválido para $Stage." }
    $resolved = if ([IO.Path]::IsPathRooted($FilePath)) {
        $FilePath
    } else {
        (Get-Command $FilePath -ErrorAction Stop).Source
    }
    $effectiveFile = $resolved
    $effectiveArguments = @($Arguments)
    if ([IO.Path]::GetExtension($resolved) -in @('.bat', '.cmd')) {
        $effectiveFile = if ([string]::IsNullOrWhiteSpace($env:COMSPEC)) {
            (Get-Command 'cmd.exe' -ErrorAction Stop).Source
        } else { $env:COMSPEC }
        $effectiveArguments = @('/d', '/c', 'call', $resolved) + @($Arguments)
    }

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $effectiveFile
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.RedirectStandardInput = $null -ne $StandardInput
    foreach ($argument in $effectiveArguments) { [void]$startInfo.ArgumentList.Add($argument) }
    $startInfo.Environment.Clear()
    foreach ($entry in (Get-PerfSafeEnvironment -Additional $AdditionalEnvironment).GetEnumerator()) {
        $startInfo.Environment[$entry.Key] = $entry.Value
    }

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { throw "No fue posible iniciar $Stage." }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if ($null -ne $StandardInput) {
            $process.StandardInput.Write($StandardInput)
            $process.StandardInput.Close()
        }
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            try { $process.Kill($true) } catch { }
            $process.WaitForExit()
            throw "$Stage excedió su timeout controlado de $TimeoutSeconds segundos."
        }
        return [pscustomobject]@{
            exitCode = $process.ExitCode
            stdout = $stdoutTask.GetAwaiter().GetResult()
            stderr = $stderrTask.GetAwaiter().GetResult()
        }
    }
    finally {
        $process.Dispose()
    }
}

function Invoke-PerfDockerProcess {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [string]$StandardInput,
        [hashtable]$AdditionalEnvironment = @{},
        [string]$Stage = 'docker',
        [int]$TimeoutSeconds = $script:PerfBaselineDockerTimeoutSeconds
    )

    return Invoke-PerfNativeProcess -FilePath 'docker' -Arguments $Arguments `
        -TimeoutSeconds $TimeoutSeconds -StandardInput $StandardInput `
        -AdditionalEnvironment $AdditionalEnvironment -Stage $Stage
}

function Get-PerfComposeArguments {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    return @(
        'compose', '--project-name', $PerfProjectName,
        '--env-file', $PerfEnvFile,
        '-f', $PerfComposeFile,
        '-f', $PerfComposeOverlay
    ) + $Arguments
}

function ConvertFrom-PerfHttpContent {
    param([Parameter(Mandatory = $true)]$Content)

    if ($Content -is [byte[]]) {
        return [Text.Encoding]::UTF8.GetString($Content)
    }
    return [string]$Content
}

function Assert-PerfArtifactPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    $allowedRoot = [IO.Path]::GetFullPath($PerfArtifactsDirectory).TrimEnd('\')
    $candidate = [IO.Path]::GetFullPath($Path)
    $expectedLocalRoot = [IO.Path]::GetFullPath(
        (Join-Path $PerfLocalData 'TiendaAbarrotes\performance\artifacts')
    ).TrimEnd('\')
    if ($allowedRoot -cne $expectedLocalRoot) {
        throw 'La raíz de artefactos no coincide con LOCALAPPDATA\TiendaAbarrotes\performance\artifacts.'
    }
    $relative = [IO.Path]::GetRelativePath($allowedRoot, $candidate)
    if ($relative -eq '..' -or $relative.StartsWith("..$([IO.Path]::DirectorySeparatorChar)")) {
        throw 'La ruta de evidencia escapa del directorio autorizado.'
    }
    if ($candidate.StartsWith([IO.Path]::GetFullPath($PerfProjectRoot), [StringComparison]::OrdinalIgnoreCase)) {
        throw 'La evidencia cruda no puede almacenarse dentro del repositorio/OneDrive.'
    }
}

function Get-PerfPathInspection {
    param([Parameter(Mandatory = $true)][string]$Path)

    $item = Get-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    if ($null -eq $item) {
        return [pscustomobject]@{
            Exists = $false
            IsContainer = $false
            Attributes = [IO.FileAttributes]::Normal
            FullName = [IO.Path]::GetFullPath($Path)
        }
    }
    return [pscustomobject]@{
        Exists = $true
        IsContainer = [bool]$item.PSIsContainer
        Attributes = [IO.FileAttributes]$item.Attributes
        FullName = [string]$item.FullName
    }
}

function Assert-PerfPathComponentsSafe {
    param(
        [Parameter(Mandatory = $true)][string]$AllowedRoot,
        [Parameter(Mandatory = $true)][string]$TargetPath,
        [scriptblock]$PathInspector
    )

    $root = [IO.Path]::GetFullPath($AllowedRoot).TrimEnd('\')
    $target = [IO.Path]::GetFullPath($TargetPath).TrimEnd('\')
    $relative = [IO.Path]::GetRelativePath($root, $target)
    if ($relative -eq '..' -or $relative.StartsWith("..$([IO.Path]::DirectorySeparatorChar)")) {
        throw 'La ruta canónica escapa de la raíz autorizada.'
    }
    if ($null -eq $PathInspector) {
        $PathInspector = { param($Component) Get-PerfPathInspection -Path $Component }
    }
    $components = [Collections.Generic.List[string]]::new()
    $components.Add($root)
    if ($relative -ne '.') {
        $current = $root
        foreach ($segment in $relative.Split(
            [IO.Path]::DirectorySeparatorChar,
            [StringSplitOptions]::RemoveEmptyEntries
        )) {
            $current = Join-Path $current $segment
            $components.Add([IO.Path]::GetFullPath($current).TrimEnd('\'))
        }
    }
    for ($index = 0; $index -lt $components.Count; $index++) {
        $component = $components[$index]
        $inspection = & $PathInspector $component
        if ($null -eq $inspection) { throw 'No se pudo inspeccionar la ruta canónica.' }
        if ($index -eq 0 -and -not $inspection.Exists) {
            throw 'La raíz autorizada de artefactos no existe.'
        }
        if (-not $inspection.Exists) { continue }
        if (-not $inspection.IsContainer) {
            throw 'Un componente de la ruta canónica no es directorio.'
        }
        if (([IO.FileAttributes]$inspection.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'La ruta canónica contiene un symlink, junction o reparse point.'
        }
        $reported = [IO.Path]::GetFullPath([string]$inspection.FullName).TrimEnd('\')
        if (-not $reported.Equals($component, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'La inspección devolvió una ruta canónica diferente.'
        }
    }
}

function Assert-PerfRunMarker {
    param([Parameter(Mandatory = $true)][string]$RunDirectory)

    $markerPath = Join-Path $RunDirectory $script:PerfBaselineMarkerName
    $markerInspection = Get-PerfPathInspection -Path $markerPath
    if (-not $markerInspection.Exists -or $markerInspection.IsContainer -or
        (([IO.FileAttributes]$markerInspection.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
        throw 'El directorio no contiene un marker regular de baseline.'
    }
    try { $marker = Get-Content -Raw -LiteralPath $markerPath | ConvertFrom-Json }
    catch { throw 'El marker del baseline no es JSON válido.' }
    if ($marker.schemaVersion -ne 1 -or
        $marker.kind -cne $script:PerfBaselineMarkerKind -or
        $marker.composeProjectName -cne $PerfProjectName -or
        $marker.runDirectoryName -cne (Split-Path -Leaf $RunDirectory)) {
        throw 'El marker no acredita una corrida de baseline autorizada.'
    }
}

function Assert-PerfBaselineRunDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$ArtifactDirectory,
        [scriptblock]$PathInspector
    )

    Assert-PerfArtifactPath -Path $ArtifactDirectory
    $root = [IO.Path]::GetFullPath($PerfArtifactsDirectory).TrimEnd('\')
    $baselineParent = [IO.Path]::GetFullPath((Join-Path $root 'baseline')).TrimEnd('\')
    $candidate = [IO.Path]::GetFullPath($ArtifactDirectory).TrimEnd('\')
    if (-not ([IO.Path]::GetDirectoryName($candidate)).Equals(
        $baselineParent,
        [StringComparison]::OrdinalIgnoreCase
    ) -or (Split-Path -Leaf $candidate) -cnotmatch $script:PerfBaselineRunNamePattern) {
        throw 'El candidato no tiene la forma canónica de una corrida baseline.'
    }
    Assert-PerfPathComponentsSafe -AllowedRoot $root -TargetPath $candidate `
        -PathInspector $PathInspector
    if (-not (Test-Path -LiteralPath $candidate -PathType Container)) {
        throw 'El candidato de baseline no existe.'
    }
    Assert-PerfRunMarker -RunDirectory $candidate
    return $candidate
}

function New-PerfBaselineArtifactDirectory {
    param([Parameter(Mandatory = $true)]$Contract)

    if ($Contract.artifacts.relativeDirectory -cne 'baseline') {
        throw 'El contrato no autoriza otra carpeta para el baseline.'
    }
    $root = [IO.Path]::GetFullPath($PerfArtifactsDirectory).TrimEnd('\')
    $baselineParent = Join-Path $root 'baseline'
    Assert-PerfPathComponentsSafe -AllowedRoot $root -TargetPath $baselineParent
    [void][IO.Directory]::CreateDirectory($baselineParent)
    Assert-PerfPathComponentsSafe -AllowedRoot $root -TargetPath $baselineParent

    $path = $null
    foreach ($attempt in 1..20) {
        $timestamp = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
        $candidate = Join-Path $baselineParent $timestamp
        if (-not (Test-Path -LiteralPath $candidate)) { $path = $candidate; break }
        Start-Sleep -Milliseconds 1
    }
    if ($null -eq $path) { throw 'No fue posible reservar un nombre canónico de baseline.' }
    Assert-PerfPathComponentsSafe -AllowedRoot $root -TargetPath $path
    [void][IO.Directory]::CreateDirectory($path)
    Assert-PerfPathComponentsSafe -AllowedRoot $root -TargetPath $path
    $marker = [ordered]@{
        schemaVersion = 1
        kind = $script:PerfBaselineMarkerKind
        composeProjectName = $PerfProjectName
        runDirectoryName = Split-Path -Leaf $path
        createdAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
    $markerPath = Join-Path $path $script:PerfBaselineMarkerName
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes(
            ($marker | ConvertTo-Json -Depth 4)
        )
        $stream = [IO.FileStream]::new(
            $markerPath,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) }
        finally { $stream.Dispose() }
        [void](Assert-PerfBaselineRunDirectory -ArtifactDirectory $path)
    }
    catch {
        Assert-PerfPathComponentsSafe -AllowedRoot $root -TargetPath $path
        if (Test-Path -LiteralPath $path -PathType Container) {
            $failedMarker = Join-Path $path $script:PerfBaselineMarkerName
            $inspection = Get-PerfPathInspection -Path $failedMarker
            if ($inspection.Exists -and -not $inspection.IsContainer -and
                (([IO.FileAttributes]$inspection.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0)) {
                [IO.File]::Delete($failedMarker)
            }
            [IO.Directory]::Delete($path, $false)
        }
        throw
    }
    return $path
}

function Enter-PerfBaselineLock {
    param([string]$LockPath = (Join-Path $PerfArtifactsDirectory 'baseline-campaign.lock'))

    Assert-PerfArtifactPath -Path $LockPath
    $parent = Split-Path -Parent $LockPath
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    try {
        $stream = [IO.FileStream]::new(
            $LockPath,
            [IO.FileMode]::OpenOrCreate,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::None
        )
        $stream.SetLength(0)
        $payload = [Text.Encoding]::UTF8.GetBytes(
            "pid=$PID;startedAt=$([DateTimeOffset]::UtcNow.ToString('o'))"
        )
        $stream.Write($payload, 0, $payload.Length)
        $stream.Flush($true)
        return $stream
    }
    catch [IO.IOException] {
        $nativeCode = $_.Exception.HResult -band 0xFFFF
        if ($nativeCode -in @(32, 33)) {
            throw [InvalidOperationException]::new(
                'Ya existe una campaña de baseline activa; no se permiten capturas simultáneas.',
                $_.Exception
            )
        }
        throw
    }
}

function Get-PerfServiceContainerId {
    param([Parameter(Mandatory = $true)][string]$Service)

    $result = Invoke-PerfDockerProcess `
        -Arguments (Get-PerfComposeArguments -Arguments @('ps', '-q', $Service)) `
        -Stage "docker-compose-ps-$Service"
    if ($result.exitCode -ne 0) { throw "No fue posible resolver el servicio $Service." }
    $containerId = $result.stdout.Trim()
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        throw "No se encontró el servicio $Service del proyecto $PerfProjectName."
    }
    return $containerId.Trim()
}

function Get-PerfContainerInspection {
    param([Parameter(Mandatory = $true)][string]$ContainerId)

    $result = Invoke-PerfDockerProcess -Arguments @('inspect', $ContainerId) -Stage 'docker-inspect'
    if ($result.exitCode -ne 0) {
        throw "No fue posible inspeccionar el contenedor $ContainerId."
    }
    $values = @(($result.stdout | ConvertFrom-Json))
    if ($values.Count -ne 1) {
        throw "Docker devolvió una inspección ambigua para $ContainerId."
    }
    return $values[0]
}

function Assert-PerfLoopbackBinding {
    param(
        [Parameter(Mandatory = $true)]$Inspection,
        [Parameter(Mandatory = $true)][string]$ContainerPort,
        [Parameter(Mandatory = $true)][int]$ExpectedHostPort
    )

    $property = $Inspection.NetworkSettings.Ports.PSObject.Properties[$ContainerPort]
    $bindings = if ($null -eq $property) { @() } else { @($property.Value) }
    if ($bindings.Count -ne 1) {
        throw "El puerto $ContainerPort debe tener exactamente un binding loopback."
    }
    if ($bindings[0].HostIp -cne '127.0.0.1' -or [int]$bindings[0].HostPort -ne $ExpectedHostPort) {
        throw "Binding inseguro para ${ContainerPort}: se esperaba 127.0.0.1:$ExpectedHostPort."
    }
}

function Assert-NoPerfK6Running {
    $result = Invoke-PerfDockerProcess -Arguments @(
        'ps', '-q',
        '--filter', "label=com.docker.compose.project=$PerfProjectName",
        '--filter', 'label=com.docker.compose.service=k6'
    ) -Stage 'docker-check-k6'
    if ($result.exitCode -ne 0) {
        throw 'No fue posible comprobar si k6 está activo.'
    }
    $containerIds = @($result.stdout -split "`r?`n") |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    if ($containerIds.Count -gt 0) {
        throw 'Hay un contenedor k6 activo; el baseline ocioso no puede capturarse bajo carga.'
    }
}

function Assert-PerfDockerAndComposeAvailable {
    $dockerInfo = Invoke-PerfDockerProcess -Arguments @('info', '--format', '{{.ServerVersion}}') `
        -Stage 'docker-info'
    if ($dockerInfo.exitCode -ne 0 -or [string]::IsNullOrWhiteSpace($dockerInfo.stdout)) {
        throw 'Docker Desktop no está disponible dentro del timeout configurado.'
    }
    $compose = Invoke-PerfDockerProcess `
        -Arguments (Get-PerfComposeArguments -Arguments @('config', '--format', 'json')) `
        -Stage 'docker-compose-config'
    if ($compose.exitCode -ne 0) { throw 'No fue posible evaluar Compose de rendimiento.' }
    $configuration = $compose.stdout | ConvertFrom-Json
    if ($configuration.name -cne $PerfProjectName) {
        throw "El nombre Compose efectivo es '$($configuration.name)', se esperaba '$PerfProjectName'."
    }
}

function Assert-PerfServiceState {
    param(
        [Parameter(Mandatory = $true)]$Inspection,
        [Parameter(Mandatory = $true)][string]$ExpectedService,
        [switch]$HealthRequired
    )

    if ($Inspection.Config.Labels.'com.docker.compose.project' -cne $PerfProjectName) {
        throw "El contenedor $ExpectedService no pertenece a $PerfProjectName."
    }
    if ($Inspection.Config.Labels.'com.docker.compose.service' -cne $ExpectedService) {
        throw "El contenedor inspeccionado no corresponde al servicio $ExpectedService."
    }
    if (-not $Inspection.State.Running) {
        throw "El servicio $ExpectedService no está en ejecución."
    }
    if ($HealthRequired -and $Inspection.State.Health.Status -cne 'healthy') {
        throw "El servicio $ExpectedService no está healthy."
    }
}

function Assert-PerfServiceInspectionSnapshot {
    param(
        [Parameter(Mandatory = $true)]$Inspection,
        [Parameter(Mandatory = $true)][string]$ExpectedService,
        [Parameter(Mandatory = $true)][int]$ExpectedRestartCount
    )

    Assert-PerfServiceState -Inspection $Inspection -ExpectedService $ExpectedService `
        -HealthRequired:($ExpectedService -in @('database', 'redis', 'backend'))
    if ([int]$Inspection.RestartCount -ne $ExpectedRestartCount) {
        throw "El servicio $ExpectedService cambió su contador de reinicios durante la captura."
    }
}

function Assert-PerfServiceContainersLive {
    param(
        [Parameter(Mandatory = $true)]$Contract,
        [Parameter(Mandatory = $true)]$ServiceContainers,
        [Parameter(Mandatory = $true)]$ExpectedRestartCounts
    )

    foreach ($service in @($Contract.requiredServices)) {
        $containerProperty = $ServiceContainers.PSObject.Properties[$service]
        $restartProperty = $ExpectedRestartCounts.PSObject.Properties[$service]
        if ($null -eq $containerProperty -or $null -eq $restartProperty) {
            throw "El preflight no conservó identidad/restarts para $service."
        }
        $inspection = Get-PerfContainerInspection -ContainerId ([string]$containerProperty.Value)
        Assert-PerfServiceInspectionSnapshot `
            -Inspection $inspection `
            -ExpectedService $service `
            -ExpectedRestartCount ([int]$restartProperty.Value)
    }
}

function New-PerfQueryPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Parameters
    )

    $query = @($Parameters.GetEnumerator() | ForEach-Object {
        '{0}={1}' -f (
            [Uri]::EscapeDataString([string]$_.Key)
        ), (
            [Uri]::EscapeDataString([string]$_.Value)
        )
    }) -join '&'
    if ([string]::IsNullOrWhiteSpace($query)) {
        return $Path
    }
    return "$Path`?$query"
}

function Assert-PerfKnownFixtures {
    param(
        [Parameter(Mandatory = $true)]$Contract,
        [Parameter(Mandatory = $true)]$FixtureContract
    )

    $token = Get-PerfBearerToken -Contract $Contract
    try {
        $presentationId = '95100000-0000-0000-0001-000000000001'
        $warehouseId = [string]$FixtureContract.warehouses[0].id
        $branchId = [string]$FixtureContract.branch.id
        $checks = @(
            [pscustomobject]@{
                name = 'known-presentation'
                path = "/api/v1/catalog/product-presentations/$presentationId"
                predicate = { param($data) $data.id -ceq $presentationId }
            },
            [pscustomobject]@{
                name = 'known-stock'
                path = New-PerfQueryPath `
                    -Path "/api/v1/inventory/stock/$presentationId" `
                    -Parameters ([ordered]@{ warehouseId = $warehouseId })
                predicate = {
                    param($data)
                    @($data | Where-Object {
                        $_.productPresentationId -ceq $presentationId -and
                        $_.warehouseId -ceq $warehouseId -and
                        [decimal]$_.availableQuantity -eq [decimal]$FixtureContract.warehouses[0].initialAvailableQuantity
                    }).Count -eq 1
                }
            },
            [pscustomobject]@{
                name = 'known-warehouses'
                path = New-PerfQueryPath `
                    -Path '/api/v1/organization/warehouses' `
                    -Parameters ([ordered]@{ branchId = $branchId })
                predicate = {
                    param($data)
                    @($data | Where-Object { $_.branchId -ceq $branchId }).Count -eq 2
                }
            }
        )
        foreach ($check in $checks) {
            $correlationId = "baseline-fixture-$([Guid]::NewGuid().ToString('N'))"
            $result = Invoke-PerfHttpGet `
                -Uri "http://127.0.0.1:$($Contract.ports.backend)$($check.path)" `
                -BearerToken $token `
                -CorrelationId $correlationId `
                -TimeoutSeconds ([int]$Contract.timeouts.httpSeconds)
            if ($result.response.StatusCode -ne 200 -or
                [string]$result.response.Headers.'X-Correlation-ID' -cne $correlationId) {
                throw "El fixture vivo $($check.name) no respondió correctamente."
            }
            $envelope = (ConvertFrom-PerfHttpContent -Content $result.response.Content) | ConvertFrom-Json
            if ($envelope.status -ne 200 -or
                $envelope.correlationId -cne $correlationId -or
                -not (& $check.predicate $envelope.data)) {
                throw "El fixture vivo $($check.name) no coincide con el contrato determinista."
            }
        }
    }
    finally {
        $token = $null
    }
}

function Assert-PerfDatabaseIdentityConfiguration {
    if ($env:POSTGRES_DB -cne $script:PerfBaselineDatabaseName -or
        $env:POSTGRES_USER -cne $script:PerfBaselineDatabaseOwner) {
        throw 'La conexión PostgreSQL no coincide con tienda_performance/tienda_owner; se canceló antes de ejecutar SQL.'
    }
}

function Assert-PerfInitialDatabaseState {
    param(
        [Parameter(Mandatory = $true)]$Contract,
        [Parameter(Mandatory = $true)]$FixtureContract
    )

    $state = (Invoke-PerfPsqlScript -SqlPath (
        Join-Path $PerfProjectRoot 'tests\performance\baseline\preflight-fixture-state.sql'
    )) | ConvertFrom-Json
    if ($state.databaseName -cne $Contract.database.name -or
        $state.databaseUser -cne $Contract.database.owner) {
        throw 'PostgreSQL devolvió una identidad distinta de tienda_performance/tienda_owner.'
    }
    $expectedStock = [decimal]$FixtureContract.warehouses[0].initialAvailableQuantity
    $invalid = [int]$state.stock.rowCount -ne 1 -or
        [decimal]$state.stock.availableQuantity -ne $expectedStock -or
        [long]$state.salesOrders -ne [long]$Contract.requiredInitialState.salesOrders -or
        [long]$state.payments -ne [long]$Contract.requiredInitialState.payments -or
        [long]$state.syncOperations -ne [long]$Contract.requiredInitialState.syncOperations -or
        [long]$state.carts -ne [long]$Contract.requiredInitialState.carts
    if ($invalid) {
        throw (
            'El estado inicial de tienda-perf está contaminado: se exige stock exacto del fixture y cero ' +
            'ventas, pagos, operaciones Sync y carritos. Recrea el ambiente con ' +
            'scripts\perf-environment-down.ps1 y scripts\perf-environment-up.ps1.'
        )
    }
    return $state
}

function Assert-PerfBaselinePreflight {
    param([Parameter(Mandatory = $true)]$Contract)

    Import-PerfEnvironment
    $script:PerfBaselineDockerTimeoutSeconds = [int]$Contract.timeouts.dockerSeconds
    $script:PerfBaselinePsqlTimeoutSeconds = [int]$Contract.timeouts.psqlSeconds
    $script:PerfBaselineHttpTimeoutSeconds = [int]$Contract.timeouts.httpSeconds
    $script:PerfBaselineLoginTimeoutSeconds = [int]$Contract.timeouts.loginSeconds
    $script:PerfBaselineDatabaseName = [string]$Contract.database.name
    $script:PerfBaselineDatabaseOwner = [string]$Contract.database.owner
    Assert-PerfDockerAndComposeAvailable

    if ($Contract.composeProjectName -cne $PerfProjectName) {
        throw 'El contrato no apunta al proyecto Compose tienda-perf.'
    }
    foreach ($portCheck in @(
        @{ Name = 'BACKEND_PORT'; Actual = $env:BACKEND_PORT; Expected = [int]$Contract.ports.backend },
        @{ Name = 'POSTGRES_PORT'; Actual = $env:POSTGRES_PORT; Expected = [int]$Contract.ports.database },
        @{ Name = 'PERF_WEB_PORT'; Actual = $env:PERF_WEB_PORT; Expected = [int]$Contract.ports.web }
    )) {
        if ([int]$portCheck.Actual -ne $portCheck.Expected) {
            throw "$($portCheck.Name) no coincide con el puerto aislado $($portCheck.Expected)."
        }
    }

    $fixtureContract = Get-Content -Raw -LiteralPath (
        Join-Path $PerfProjectRoot 'tests\performance\fixtures\fixture-contract.json'
    ) | ConvertFrom-Json
    if ($fixtureContract.dataset -cne $Contract.dataset) {
        throw 'El dataset del baseline no coincide con los fixtures cargados.'
    }

    $serviceContainers = [ordered]@{}
    $serviceRestartCounts = [ordered]@{}
    foreach ($service in @($Contract.requiredServices)) {
        $containerId = Get-PerfServiceContainerId -Service $service
        $inspection = Get-PerfContainerInspection -ContainerId $containerId
        Assert-PerfServiceInspectionSnapshot -Inspection $inspection `
            -ExpectedService $service -ExpectedRestartCount ([int]$inspection.RestartCount)
        $serviceContainers[$service] = $containerId
        $serviceRestartCounts[$service] = [int]$inspection.RestartCount

        switch ($service) {
            'database' {
                Assert-PerfLoopbackBinding -Inspection $inspection -ContainerPort '5432/tcp' `
                    -ExpectedHostPort ([int]$Contract.ports.database)
            }
            'backend' {
                Assert-PerfLoopbackBinding -Inspection $inspection -ContainerPort '8080/tcp' `
                    -ExpectedHostPort ([int]$Contract.ports.backend)
            }
            'web-performance' {
                Assert-PerfLoopbackBinding -Inspection $inspection -ContainerPort '80/tcp' `
                    -ExpectedHostPort ([int]$Contract.ports.web)
            }
            'redis' {
                if ($inspection.NetworkSettings.Ports.PSObject.Properties.Value.Count -gt 0) {
                    throw 'Redis no debe publicar puertos al host durante la campaña.'
                }
            }
        }
    }

    Assert-NoPerfK6Running
    Assert-PerfArtifactPath -Path (Join-Path $PerfArtifactsDirectory 'baseline')

    & (Join-Path $PerfProjectRoot 'tests\performance\fixtures\validate-performance-fixtures.ps1')
    if (-not $?) {
        throw 'Los fixtures conocidos no superaron su validación.'
    }
    [void](Assert-PerfInitialDatabaseState -Contract $Contract -FixtureContract $fixtureContract)

    $correlationId = "baseline-preflight-$([Guid]::NewGuid().ToString('N'))"
    $readiness = Invoke-WebRequest `
        -Uri "http://127.0.0.1:$($Contract.ports.backend)/actuator/health/readiness" `
        -Headers @{ 'X-Correlation-ID' = $correlationId } `
        -TimeoutSec ([int]$Contract.timeouts.httpSeconds) `
        -SkipHttpErrorCheck
    $readinessText = ConvertFrom-PerfHttpContent -Content $readiness.Content
    if ($readiness.StatusCode -ne 200 -or
        ($readinessText | ConvertFrom-Json).status -cne 'UP' -or
        [string]$readiness.Headers.'X-Correlation-ID' -cne $correlationId) {
        throw 'Readiness no confirmó UP con correlación en el entorno aislado.'
    }
    Assert-PerfKnownFixtures -Contract $Contract -FixtureContract $fixtureContract

    return [pscustomobject]@{
        serviceContainers = [pscustomobject]$serviceContainers
        serviceRestartCounts = [pscustomobject]$serviceRestartCounts
        fixtureContract = $fixtureContract
    }
}

function Invoke-PerfPsqlScript {
    param([Parameter(Mandatory = $true)][string]$SqlPath)

    Assert-PerfDatabaseIdentityConfiguration
    $databaseContainerId = Get-PerfServiceContainerId -Service 'database'
    $inspection = Get-PerfContainerInspection -ContainerId $databaseContainerId
    Assert-PerfServiceState -Inspection $inspection -ExpectedService 'database' -HealthRequired

    $arguments = @(
        'exec', '-i', '-e', 'PGPASSWORD', $databaseContainerId,
        'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1',
        '-U', $env:POSTGRES_USER, '-d', $env:POSTGRES_DB
    )
    $identityGuard = @"
\set ON_ERROR_STOP on
DO `$perf_identity`$
BEGIN
    IF current_database() <> '$($script:PerfBaselineDatabaseName)'
       OR current_user <> '$($script:PerfBaselineDatabaseOwner)' THEN
        RAISE EXCEPTION 'Unexpected performance database identity';
    END IF;
END
`$perf_identity`$;
"@
    $sqlInput = $identityGuard + [Environment]::NewLine + (
        Get-Content -Raw -LiteralPath $SqlPath
    )
    $result = Invoke-PerfDockerProcess -Arguments $arguments `
        -StandardInput $sqlInput `
        -AdditionalEnvironment @{ PGPASSWORD = $env:POSTGRES_PASSWORD } `
        -Stage "psql-$(Split-Path -Leaf $SqlPath)" `
        -TimeoutSeconds $script:PerfBaselinePsqlTimeoutSeconds
    if ($result.exitCode -ne 0) {
        throw "psql falló para $(Split-Path -Leaf $SqlPath) sin exponer su salida sensible."
    }
    return $result.stdout.Trim()
}

function Initialize-PerfPostgresObservability {
    Assert-PerfDockerAndComposeAvailable
    $sqlPath = Join-Path $PerfProjectRoot 'tests\performance\baseline\prepare-postgres.sql'
    [void](Invoke-PerfPsqlScript -SqlPath $sqlPath)

    $databaseContainerId = Get-PerfServiceContainerId -Service 'database'
    $result = Invoke-PerfDockerProcess -Arguments @(
        'exec', '-e', 'PGPASSWORD', $databaseContainerId,
        'psql', '-X', '-At', '-v', 'ON_ERROR_STOP=1',
        '-U', $env:POSTGRES_USER, '-d', $env:POSTGRES_DB,
        '-c', "SELECT current_database() || '|' || current_user || '|' || extname FROM pg_extension WHERE extname = 'pg_stat_statements'"
    ) -AdditionalEnvironment @{ PGPASSWORD = $env:POSTGRES_PASSWORD } `
        -Stage 'psql-verify-pg-stat-statements' `
        -TimeoutSeconds $script:PerfBaselinePsqlTimeoutSeconds
    $expected = "$($script:PerfBaselineDatabaseName)|$($script:PerfBaselineDatabaseOwner)|pg_stat_statements"
    if ($result.exitCode -ne 0 -or $result.stdout.Trim() -cne $expected) {
        throw 'pg_stat_statements no quedó disponible en tienda-perf.'
    }
}

function Reset-PerfPostgresStatements {
    $sqlPath = Join-Path $PerfProjectRoot 'tests\performance\baseline\reset-postgres-statements.sql'
    [void](Invoke-PerfPsqlScript -SqlPath $sqlPath)
}

function Get-PerfPostgresSnapshot {
    $sqlPath = Join-Path $PerfProjectRoot 'tests\performance\baseline\postgres-snapshot.sql'
    $json = Invoke-PerfPsqlScript -SqlPath $sqlPath
    if ([string]::IsNullOrWhiteSpace($json)) {
        throw 'PostgreSQL no devolvió la instantánea esperada.'
    }
    return $json | ConvertFrom-Json
}

function Get-PerfBearerToken {
    param([Parameter(Mandatory = $true)]$Contract)

    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "http://127.0.0.1:$($Contract.ports.backend)/api/v1/auth/login" `
        -ContentType 'application/json' `
        -TimeoutSec ([int]$Contract.timeouts.loginSeconds) `
        -Headers @{ 'X-Correlation-ID' = "baseline-login-$([Guid]::NewGuid().ToString('N'))" } `
        -Body (@{ username = $env:PERF_USERNAME; password = $env:PERF_PASSWORD } | ConvertTo-Json)
    if ([string]::IsNullOrWhiteSpace($response.data.accessToken)) {
        throw 'El login restringido no devolvió un token utilizable.'
    }
    return [string]$response.data.accessToken
}

function New-PerfHttpHeaders {
    param(
        [Parameter(Mandatory = $true)][string]$CorrelationId,
        [string]$BearerToken,
        [string]$Accept = 'application/json'
    )

    $headers = @{ 'X-Correlation-ID' = $CorrelationId; Accept = $Accept }
    if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
        $headers.Authorization = "Bearer $BearerToken"
    }
    return $headers
}

function Invoke-PerfHttpGet {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [string]$BearerToken,
        [Parameter(Mandatory = $true)][string]$CorrelationId,
        [string]$Accept = 'application/json',
        [int]$TimeoutSeconds = $script:PerfBaselineHttpTimeoutSeconds
    )

    $headers = New-PerfHttpHeaders `
        -CorrelationId $CorrelationId `
        -BearerToken $BearerToken `
        -Accept $Accept
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    if ($TimeoutSeconds -le 0) { throw 'El timeout HTTP debe ser positivo.' }
    $response = Invoke-WebRequest -Method Get -Uri $Uri -Headers $headers `
        -TimeoutSec $TimeoutSeconds -SkipHttpErrorCheck
    $stopwatch.Stop()
    return [pscustomobject]@{
        response = $response
        durationMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 3)
    }
}

function Resolve-PerfWarmupPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$FixtureContract
    )

    return $Path.Replace(
        '{productPresentationId}', '95100000-0000-0000-0001-000000000001'
    ).Replace(
        '{warehouseId}', [string]$FixtureContract.warehouses[0].id
    ).Replace(
        '{branchId}', [string]$FixtureContract.branch.id
    )
}

function Assert-PerfWarmupResponse {
    param(
        [Parameter(Mandatory = $true)]$Endpoint,
        [Parameter(Mandatory = $true)]$Response,
        [Parameter(Mandatory = $true)][string]$CorrelationId,
        [Parameter(Mandatory = $true)][string]$ResolvedPath
    )

    if ($Response.StatusCode -ne 200) {
        throw "$($Endpoint.name) respondió HTTP $($Response.StatusCode) durante el calentamiento."
    }
    $headerCorrelation = [string]$Response.Headers.'X-Correlation-ID'
    if ($headerCorrelation -cne $CorrelationId) {
        throw "$($Endpoint.name) no devolvió la correlación esperada."
    }
    $body = (ConvertFrom-PerfHttpContent -Content $Response.Content) | ConvertFrom-Json
    if ($Endpoint.apiEnvelope) {
        if ($body.status -ne 200 -or
            [string]::IsNullOrWhiteSpace($body.code) -or
            $body.correlationId -cne $CorrelationId -or
            $body.path -cne $ResolvedPath.Split('?')[0]) {
            throw "$($Endpoint.name) no devolvió un ApiResponseDto válido."
        }
    }
    elseif ($body.status -cne 'UP') {
        throw "$($Endpoint.name) no devolvió el estado Actuator UP."
    }
}

function Invoke-PerfWarmup {
    param(
        [Parameter(Mandatory = $true)]$Contract,
        [Parameter(Mandatory = $true)]$FixtureContract,
        [Parameter(Mandatory = $true)][string]$BearerToken
    )

    $results = [System.Collections.Generic.List[object]]::new()
    foreach ($endpoint in @($Contract.warmup.endpoints)) {
        $path = Resolve-PerfWarmupPath -Path $endpoint.path -FixtureContract $FixtureContract
        $uri = "http://127.0.0.1:$($Contract.ports.backend)$path"
        foreach ($iteration in 1..([int]$Contract.warmup.requestsPerEndpoint)) {
            $correlationId = "baseline-warm-$([Guid]::NewGuid().ToString('N'))"
            $token = if ($endpoint.authenticated) { $BearerToken } else { $null }
            $result = Invoke-PerfHttpGet -Uri $uri -BearerToken $token `
                -CorrelationId $correlationId -TimeoutSeconds ([int]$Contract.timeouts.httpSeconds)
            Assert-PerfWarmupResponse -Endpoint $endpoint -Response $result.response `
                -CorrelationId $correlationId -ResolvedPath $path
            $results.Add([pscustomobject]@{
                endpoint = $endpoint.name
                iteration = $iteration
                capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
                durationMs = $result.durationMs
                status = [int]$result.response.StatusCode
            })
        }
    }
    return @($results)
}

function ConvertFrom-PerfPrometheus {
    param(
        [Parameter(Mandatory = $true)][string]$Content,
        [Parameter(Mandatory = $true)][string[]]$MetricFamilies
    )

    $values = [ordered]@{}
    foreach ($family in $MetricFamilies) { $values[$family] = 0.0 }
    $seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in ($Content -split "`r?`n")) {
        if ($line.StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -notmatch '^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?\s+([^\s]+)') { continue }
        $name = $matches[1]
        if ($name -notin $MetricFamilies) { continue }
        $number = 0.0
        if ([double]::TryParse(
            $matches[3],
            [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$number
        ) -and [double]::IsFinite($number)) {
            $values[$name] = [double]$values[$name] + $number
            [void]$seen.Add($name)
        }
    }
    foreach ($family in $MetricFamilies) {
        if (-not $seen.Contains($family)) { $values[$family] = $null }
    }
    return [pscustomobject]$values
}

function Get-PerfPrometheusSnapshot {
    param(
        [Parameter(Mandatory = $true)]$Contract,
        [Parameter(Mandatory = $true)][string]$BearerToken
    )

    $correlationId = "baseline-prom-$([Guid]::NewGuid().ToString('N'))"
    $result = Invoke-PerfHttpGet `
        -Uri "http://127.0.0.1:$($Contract.ports.backend)/actuator/prometheus" `
        -BearerToken $BearerToken `
        -CorrelationId $correlationId `
        -Accept 'text/plain' `
        -TimeoutSeconds ([int]$Contract.timeouts.httpSeconds)
    if ($result.response.StatusCode -ne 200 -or
        [string]$result.response.Headers.'X-Correlation-ID' -cne $correlationId) {
        throw 'Actuator/Prometheus no respondió autenticado con correlación válida.'
    }
    return [pscustomobject]@{
        content = ConvertFrom-PerfHttpContent -Content $result.response.Content
        selected = ConvertFrom-PerfPrometheus `
            -Content (ConvertFrom-PerfHttpContent -Content $result.response.Content) `
            -MetricFamilies @(
                @($Contract.prometheusMetrics.critical) +
                @($Contract.prometheusMetrics.optional)
            )
        durationMs = $result.durationMs
    }
}

function Convert-PerfSizeToBytes {
    param([Parameter(Mandatory = $true)][string]$Value)

    $normalized = $Value.Trim()
    if ($normalized -notmatch '^([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?i?B)$') { return $null }
    $number = [double]::Parse($matches[1], [Globalization.CultureInfo]::InvariantCulture)
    $factor = switch ($matches[2].ToUpperInvariant()) {
        'B' { 1 }
        'KB' { 1KB }
        'KIB' { 1KB }
        'MB' { 1MB }
        'MIB' { 1MB }
        'GB' { 1GB }
        'GIB' { 1GB }
        'TB' { 1TB }
        'TIB' { 1TB }
    }
    return [Math]::Round($number * $factor, 0)
}

function Get-PerfDockerStats {
    param([Parameter(Mandatory = $true)]$ServiceContainers)

    $ids = @($ServiceContainers.PSObject.Properties.Value)
    $capture = Invoke-PerfDockerProcess `
        -Arguments (@('stats', '--no-stream', '--format', '{{json .}}') + $ids) `
        -Stage 'docker-stats'
    if ($capture.exitCode -ne 0) { throw 'docker stats no pudo capturar el baseline.' }
    $lines = @($capture.stdout -split "`r?`n")
    $nameById = @{}
    foreach ($property in $ServiceContainers.PSObject.Properties) {
        $nameById[[string]$property.Value] = $property.Name
    }
    $result = [System.Collections.Generic.List[object]]::new()
    foreach ($line in $lines) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $raw = $line | ConvertFrom-Json
        $id = @($nameById.Keys | Where-Object { $_.StartsWith([string]$raw.Container) })[0]
        $memoryUsed = ([string]$raw.MemUsage -split '\s*/\s*')[0]
        $cpu = [double]::Parse(
            ([string]$raw.CPUPerc).TrimEnd('%'),
            [Globalization.CultureInfo]::InvariantCulture
        )
        $result.Add([pscustomobject]@{
            service = $nameById[$id]
            containerId = [string]$raw.Container
            cpuPercent = $cpu
            memoryUsedBytes = Convert-PerfSizeToBytes -Value $memoryUsed
            memoryPercent = [double]::Parse(
                ([string]$raw.MemPerc).TrimEnd('%'),
                [Globalization.CultureInfo]::InvariantCulture
            )
            pids = [int]$raw.PIDs
            networkIo = [string]$raw.NetIO
            blockIo = [string]$raw.BlockIO
        })
    }
    return @($result)
}

function Test-PerfFiniteNumber {
    param($Value, [switch]$NonNegative)

    if ($null -eq $Value) { return $false }
    $number = 0.0
    if (-not [double]::TryParse(
        [string]$Value,
        [Globalization.NumberStyles]::Float,
        [Globalization.CultureInfo]::InvariantCulture,
        [ref]$number
    )) { return $false }
    if (-not [double]::IsFinite($number)) { return $false }
    return -not $NonNegative -or $number -ge 0
}

function Assert-PerfMetricSample {
    param(
        [Parameter(Mandatory = $true)]$Sample,
        [Parameter(Mandatory = $true)]$Contract
    )

    if ($Sample.readiness.status -cne 'UP') { throw 'La muestra no conserva readiness UP.' }
    foreach ($timingValue in @(
        $Sample.readiness.durationMs,
        $Sample.prometheusScrapeDurationMs,
        $Sample.timing.jitterMilliseconds,
        $Sample.timing.captureDurationMilliseconds
    )) {
        if (-not (Test-PerfFiniteNumber -Value $timingValue -NonNegative)) {
            throw 'La muestra contiene timing null, no finito o negativo.'
        }
    }
    if ([double]$Sample.timing.jitterMilliseconds -gt [double]$Contract.timing.maximumJitterMilliseconds) {
        throw 'La muestra excedió la tolerancia de jitter del contrato.'
    }

    foreach ($metric in @($Contract.prometheusMetrics.critical)) {
        if (-not (Test-PerfFiniteNumber -Value $Sample.prometheus.$metric -NonNegative)) {
            throw "La métrica Prometheus crítica $metric está ausente o no es finita."
        }
    }
    foreach ($metric in @($Contract.prometheusMetrics.optional)) {
        $value = $Sample.prometheus.$metric
        if ($null -ne $value -and -not (Test-PerfFiniteNumber -Value $value -NonNegative)) {
            throw "La métrica Prometheus opcional $metric existe pero no es finita."
        }
    }

    $dockerRows = @($Sample.docker)
    if ($dockerRows.Count -ne @($Contract.requiredServices).Count) {
        throw 'La muestra Docker no contiene exactamente todos los servicios requeridos.'
    }
    foreach ($service in @($Contract.requiredServices)) {
        $rows = @($dockerRows | Where-Object service -ceq $service)
        if ($rows.Count -ne 1) { throw "La muestra Docker tiene ausencia o duplicado de $service." }
        foreach ($field in @('cpuPercent', 'memoryUsedBytes', 'memoryPercent', 'pids')) {
            if (-not (Test-PerfFiniteNumber -Value $rows[0].$field -NonNegative)) {
                throw "La métrica Docker $service.$field es inválida."
            }
        }
    }

    if ($Sample.postgres.databaseName -cne $Contract.database.name -or
        $Sample.postgres.databaseUser -cne $Contract.database.owner) {
        throw 'La muestra PostgreSQL proviene de una identidad no autorizada.'
    }
    foreach ($value in @(
        $Sample.postgres.database.numbackends,
        $Sample.postgres.database.xact_commit,
        $Sample.postgres.database.xact_rollback,
        $Sample.postgres.database.blks_read,
        $Sample.postgres.database.blks_hit,
        $Sample.postgres.database.temp_files,
        $Sample.postgres.database.temp_bytes,
        $Sample.postgres.database.deadlocks,
        $Sample.postgres.activity.active_sessions,
        $Sample.postgres.activity.waiting_sessions,
        $Sample.postgres.activity.total_sessions,
        $Sample.postgres.locks.granted_locks,
        $Sample.postgres.locks.waiting_locks
    )) {
        if (-not (Test-PerfFiniteNumber -Value $value -NonNegative)) {
            throw 'La muestra PostgreSQL contiene una métrica requerida inválida.'
        }
    }
    if ($null -eq $Sample.postgres.topStatements) {
        throw 'La muestra PostgreSQL no contiene pg_stat_statements.'
    }
}

function Get-PerfScheduledSampleTime {
    param(
        [Parameter(Mandatory = $true)][DateTimeOffset]$WindowStart,
        [Parameter(Mandatory = $true)][int]$Index,
        [Parameter(Mandatory = $true)][int]$IntervalSeconds
    )

    if ($Index -lt 0 -or $IntervalSeconds -le 0) { throw 'Índice o intervalo temporal inválido.' }
    return $WindowStart.AddSeconds([double]$Index * $IntervalSeconds)
}

function Invoke-PerfMetricWindow {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('preWarm', 'postWarm')][string]$Name,
        [Parameter(Mandatory = $true)]$Window,
        [Parameter(Mandatory = $true)]$Contract,
        [Parameter(Mandatory = $true)][string]$BearerToken,
        [Parameter(Mandatory = $true)]$ServiceContainers,
        [Parameter(Mandatory = $true)]$ExpectedRestartCounts,
        [Parameter(Mandatory = $true)][string]$ArtifactDirectory
    )

    $calculatedSamples = [Math]::Floor(
        [int]$Window.durationSeconds / [int]$Window.intervalSeconds
    ) + 1
    if ($calculatedSamples -ne [int]$Window.expectedSamples) {
        throw "La ventana $Name tiene un número de muestras incoherente."
    }
    $windowDirectory = Join-Path $ArtifactDirectory $Name
    $prometheusDirectory = Join-Path $windowDirectory 'prometheus'
    New-Item -ItemType Directory -Force -Path $prometheusDirectory | Out-Null
    $samplePath = Join-Path $windowDirectory 'samples.jsonl'
    $samples = [System.Collections.Generic.List[object]]::new()
    $windowStart = [DateTimeOffset]::UtcNow

    foreach ($index in 0..($calculatedSamples - 1)) {
        $scheduledAt = Get-PerfScheduledSampleTime `
            -WindowStart $windowStart -Index $index -IntervalSeconds ([int]$Window.intervalSeconds)
        $waitMilliseconds = [Math]::Ceiling(($scheduledAt - [DateTimeOffset]::UtcNow).TotalMilliseconds)
        if ($waitMilliseconds -gt 0) { Start-Sleep -Milliseconds $waitMilliseconds }
        Assert-NoPerfK6Running
        Assert-PerfServiceContainersLive `
            -Contract $Contract `
            -ServiceContainers $ServiceContainers `
            -ExpectedRestartCounts $ExpectedRestartCounts
        $iterationTimer = [Diagnostics.Stopwatch]::StartNew()
        $actualAt = [DateTimeOffset]::UtcNow
        $jitterMilliseconds = [Math]::Abs(($actualAt - $scheduledAt).TotalMilliseconds)
        $prometheus = Get-PerfPrometheusSnapshot -Contract $Contract -BearerToken $BearerToken
        $dockerStats = Get-PerfDockerStats -ServiceContainers $ServiceContainers
        $postgres = Get-PerfPostgresSnapshot
        $readinessCorrelation = "baseline-health-$([Guid]::NewGuid().ToString('N'))"
        $readiness = Invoke-PerfHttpGet `
            -Uri "http://127.0.0.1:$($Contract.ports.backend)/actuator/health/readiness" `
            -CorrelationId $readinessCorrelation `
            -TimeoutSeconds ([int]$Contract.timeouts.httpSeconds)
        $readinessBody = (ConvertFrom-PerfHttpContent -Content $readiness.response.Content) | ConvertFrom-Json
        if ($readiness.response.StatusCode -ne 200 -or $readinessBody.status -cne 'UP') {
            throw "Readiness dejó de estar UP durante $Name."
        }
        $iterationTimer.Stop()

        $rawPrometheusPath = Join-Path $prometheusDirectory ('{0:D3}.prom' -f $index)
        [IO.File]::WriteAllText(
            $rawPrometheusPath,
            $prometheus.content,
            [Text.UTF8Encoding]::new($false)
        )
        $sample = [pscustomobject]@{
            index = $index
            capturedAt = $actualAt.ToString('o')
            timing = [pscustomobject]@{
                scheduledAt = $scheduledAt.ToString('o')
                actualAt = $actualAt.ToString('o')
                jitterMilliseconds = [Math]::Round($jitterMilliseconds, 3)
                captureDurationMilliseconds = [Math]::Round($iterationTimer.Elapsed.TotalMilliseconds, 3)
            }
            readiness = [pscustomobject]@{
                status = $readinessBody.status
                durationMs = $readiness.durationMs
            }
            prometheus = $prometheus.selected
            prometheusScrapeDurationMs = $prometheus.durationMs
            docker = $dockerStats
            postgres = $postgres
        }
        Assert-PerfMetricSample -Sample $sample -Contract $Contract
        $samples.Add($sample)
        [IO.File]::AppendAllText(
            $samplePath,
            (($sample | ConvertTo-Json -Depth 12 -Compress) + [Environment]::NewLine),
            [Text.UTF8Encoding]::new($false)
        )
    }
    $windowCompletedAt = [DateTimeOffset]::UtcNow
    $targetCompletedAt = $windowStart.AddSeconds([double]$Window.durationSeconds)
    $overrunSeconds = [Math]::Max(0, ($windowCompletedAt - $targetCompletedAt).TotalSeconds)
    if ($overrunSeconds -gt [double]$Contract.timing.maximumWindowOverrunSeconds) {
        throw "La ventana $Name excedió la tolerancia total de duración."
    }
    [pscustomobject]@{
        name = $Name
        scheduledStart = $windowStart.ToString('o')
        actualCompletedAt = $windowCompletedAt.ToString('o')
        scheduledDurationSeconds = [int]$Window.durationSeconds
        actualDurationSeconds = [Math]::Round(($windowCompletedAt - $windowStart).TotalSeconds, 3)
        overrunSeconds = [Math]::Round($overrunSeconds, 3)
        maximumObservedJitterMilliseconds = [Math]::Round((
            $samples.timing.jitterMilliseconds | Measure-Object -Maximum
        ).Maximum, 3)
        sampleCount = $samples.Count
    } | ConvertTo-Json -Depth 5 | Set-Content `
        -LiteralPath (Join-Path $windowDirectory 'window-timing.json') -Encoding utf8NoBOM
    return @($samples)
}

function Get-PerfMedian {
    param([Parameter(Mandatory = $true)][double[]]$Values)

    $sorted = @($Values | Where-Object { [double]::IsFinite($_) } | Sort-Object)
    if ($sorted.Count -eq 0) { return $null }
    $middle = [Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) { return [double]$sorted[$middle] }
    return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2.0
}

function Get-PerfSeriesSummary {
    param([object[]]$Values)

    $numbers = @($Values | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ })
    if ($numbers.Count -eq 0) {
        return [pscustomobject]@{ median = $null; first = $null; last = $null; delta = $null }
    }
    return [pscustomobject]@{
        median = Get-PerfMedian -Values $numbers
        first = $numbers[0]
        last = $numbers[-1]
        delta = $numbers[-1] - $numbers[0]
    }
}

function Get-PerfPostWarmMedian {
    param(
        [Parameter(Mandatory = $true)][object[]]$Samples,
        [Parameter(Mandatory = $true)]$Contract
    )

    if ($Contract.officialBaselineWindow -cne 'postWarm') {
        throw 'El contrato no permite declarar otra ventana como baseline oficial.'
    }
    if ($Samples.Count -ne [int]$Contract.sampling.postWarm.expectedSamples) {
        throw 'La ventana postWarm no contiene todas las muestras oficiales.'
    }
    foreach ($sample in $Samples) { Assert-PerfMetricSample -Sample $sample -Contract $Contract }

    $prometheus = [ordered]@{}
    foreach ($family in @($Contract.prometheusMetrics.critical) + @($Contract.prometheusMetrics.optional)) {
        $prometheus[$family] = Get-PerfSeriesSummary -Values @(
            $Samples | ForEach-Object { $_.prometheus.$family }
        )
    }
    $docker = [ordered]@{}
    foreach ($service in @($Contract.requiredServices)) {
        $rows = @($Samples | ForEach-Object { $_.docker | Where-Object service -eq $service })
        $docker[$service] = [pscustomobject]@{
            cpuPercent = Get-PerfSeriesSummary -Values @($rows.cpuPercent)
            memoryUsedBytes = Get-PerfSeriesSummary -Values @($rows.memoryUsedBytes)
            memoryPercent = Get-PerfSeriesSummary -Values @($rows.memoryPercent)
            pids = Get-PerfSeriesSummary -Values @($rows.pids)
        }
    }
    $postgres = [pscustomobject]@{
        numBackends = Get-PerfSeriesSummary -Values @($Samples.postgres.database.numbackends)
        xactRollbacks = Get-PerfSeriesSummary -Values @($Samples.postgres.database.xact_rollback)
        bufferReads = Get-PerfSeriesSummary -Values @($Samples.postgres.database.blks_read)
        bufferHits = Get-PerfSeriesSummary -Values @($Samples.postgres.database.blks_hit)
        activeSessions = Get-PerfSeriesSummary -Values @($Samples.postgres.activity.active_sessions)
        waitingSessions = Get-PerfSeriesSummary -Values @($Samples.postgres.activity.waiting_sessions)
        waitingLocks = Get-PerfSeriesSummary -Values @($Samples.postgres.locks.waiting_locks)
        deadlocks = Get-PerfSeriesSummary -Values @($Samples.postgres.database.deadlocks)
    }
    return [pscustomobject]@{
        window = 'postWarm'
        sampleCount = $Samples.Count
        prometheus = [pscustomobject]$prometheus
        docker = [pscustomobject]$docker
        postgres = $postgres
    }
}

function Get-PerfCommandVersion {
    param([Parameter(Mandatory = $true)][string]$Command, [string[]]$Arguments = @())

    try {
        $result = Invoke-PerfNativeProcess -FilePath $Command -Arguments $Arguments `
            -TimeoutSeconds 30 -Stage "version-$Command"
        if ($result.exitCode -ne 0) { return "unavailable (exit $($result.exitCode))" }
        return (($result.stdout + $result.stderr).Trim())
    }
    catch { return 'unavailable (timeout or start failure)' }
}

function Get-PerfEnvironmentMetadata {
    param([Parameter(Mandatory = $true)]$ServiceContainers)

    $cpu = Get-CimInstance Win32_Processor | Select-Object Name, NumberOfCores, NumberOfLogicalProcessors
    $computer = Get-CimInstance Win32_ComputerSystem
    $gitStatus = Invoke-PerfNativeProcess -FilePath 'git' `
        -Arguments @('-C', $PerfProjectRoot, 'status', '--porcelain') `
        -TimeoutSeconds 30 -Stage 'git-status'
    if ($gitStatus.exitCode -ne 0) { throw 'No se pudo determinar el estado Git del baseline.' }
    $gitDirtyCount = @($gitStatus.stdout -split "`r?`n" |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
    $containers = [ordered]@{}
    foreach ($property in $ServiceContainers.PSObject.Properties) {
        $inspection = Get-PerfContainerInspection -ContainerId ([string]$property.Value)
        $containers[$property.Name] = [pscustomobject]@{
            image = [string]$inspection.Config.Image
            imageId = [string]$inspection.Image
            memoryLimitBytes = [long]$inspection.HostConfig.Memory
            nanoCpus = [long]$inspection.HostConfig.NanoCpus
        }
    }
    return [pscustomobject]@{
        capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
        gitRevision = (Get-PerfCommandVersion -Command git -Arguments @('-C', $PerfProjectRoot, 'rev-parse', 'HEAD'))
        gitDirtyEntryCount = $gitDirtyCount
        operatingSystem = [Environment]::OSVersion.VersionString
        powershell = $PSVersionTable.PSVersion.ToString()
        java = Get-PerfCommandVersion -Command java -Arguments @('-version')
        node = Get-PerfCommandVersion -Command node -Arguments @('--version')
        npm = Get-PerfCommandVersion -Command npm -Arguments @('--version')
        docker = Get-PerfCommandVersion -Command docker -Arguments @('version', '--format', '{{.Server.Version}}')
        dockerCompose = Get-PerfCommandVersion -Command docker -Arguments @('compose', 'version', '--short')
        cpu = @($cpu)
        totalPhysicalMemoryBytes = [long]$computer.TotalPhysicalMemory
        safeConfiguration = [pscustomobject]@{
            databasePoolMaximum = [int]$env:DB_POOL_MAX_SIZE
            databasePoolMinimumIdle = [int]$env:DB_POOL_MIN_IDLE
            backendPort = [int]$env:BACKEND_PORT
            databasePort = [int]$env:POSTGRES_PORT
            webPort = [int]$env:PERF_WEB_PORT
        }
        containers = [pscustomobject]$containers
    }
}

function Invoke-PerfLoggedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$LogPath,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][string]$Stage
    )

    $result = Invoke-PerfNativeProcess -FilePath $Command -Arguments $Arguments `
        -TimeoutSeconds $TimeoutSeconds -WorkingDirectory $WorkingDirectory -Stage $Stage
    [IO.File]::WriteAllText(
        $LogPath,
        ($result.stdout + $result.stderr),
        [Text.UTF8Encoding]::new($false)
    )
    if ($result.exitCode -ne 0) {
        throw "$Stage terminó con código $($result.exitCode). Consulta $(Split-Path -Leaf $LogPath)."
    }
}

function Invoke-PerfProjectVerification {
    param(
        [Parameter(Mandatory = $true)][string]$ArtifactDirectory,
        [Parameter(Mandatory = $true)]$Contract
    )

    $verificationDirectory = Join-Path $ArtifactDirectory 'verification'
    New-Item -ItemType Directory -Force -Path $verificationDirectory | Out-Null
    Invoke-PerfLoggedCommand `
        -WorkingDirectory (Join-Path $PerfProjectRoot 'apps\backend') `
        -Command (Join-Path $PerfProjectRoot 'apps\backend\gradlew.bat') `
        -Arguments @('test', '--no-daemon') `
        -LogPath (Join-Path $verificationDirectory 'backend-test.log') `
        -TimeoutSeconds ([int]$Contract.timeouts.backendTestSeconds) -Stage 'backend-test'
    Invoke-PerfLoggedCommand `
        -WorkingDirectory (Join-Path $PerfProjectRoot 'apps\web') `
        -Command 'npm.cmd' -Arguments @('test', '--', '--watch=false') `
        -LogPath (Join-Path $verificationDirectory 'web-test.log') `
        -TimeoutSeconds ([int]$Contract.timeouts.webTestSeconds) -Stage 'web-test'
    Invoke-PerfLoggedCommand `
        -WorkingDirectory (Join-Path $PerfProjectRoot 'apps\web') `
        -Command 'npm.cmd' -Arguments @('run', 'build') `
        -LogPath (Join-Path $verificationDirectory 'web-build.log') `
        -TimeoutSeconds ([int]$Contract.timeouts.webBuildSeconds) -Stage 'web-build'
}

function Get-PerfAngularBuildManifest {
    $distRoot = Join-Path $PerfProjectRoot 'apps\web\dist\tienda-web\browser'
    if (-not (Test-Path -LiteralPath $distRoot -PathType Container)) {
        throw 'No existe el build Angular de producción esperado.'
    }
    $files = @(Get-ChildItem -LiteralPath $distRoot -Recurse -File | ForEach-Object {
        [pscustomobject]@{
            path = [IO.Path]::GetRelativePath($distRoot, $_.FullName).Replace('\', '/')
            bytes = $_.Length
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    })
    return [pscustomobject]@{
        root = 'apps/web/dist/tienda-web/browser'
        fileCount = $files.Count
        totalBytes = ($files | Measure-Object -Property bytes -Sum).Sum
        files = $files
    }
}

function New-PerfBaselineSummary {
    param(
        [switch]$SkipProjectVerification,
        [Parameter(Mandatory = $true)][DateTimeOffset]$StartedAt,
        [Parameter(Mandatory = $true)][DateTimeOffset]$CompletedAt,
        [Parameter(Mandatory = $true)]$Contract,
        [Parameter(Mandatory = $true)]$Environment,
        [Parameter(Mandatory = $true)]$AngularBuild,
        [Parameter(Mandatory = $true)][int]$PreWarmSampleCount,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$WarmupSummary,
        [Parameter(Mandatory = $true)]$PostWarmStatistics,
        $PreWarmTiming,
        $PostWarmTiming
    )

    $official = -not [bool]$SkipProjectVerification
    $summary = [ordered]@{
        schemaVersion = 1
        status = if ($official) { 'COMPLETED' } else { 'DIAGNOSTIC' }
        official = $official
        startedAt = $StartedAt.ToString('o')
        completedAt = $CompletedAt.ToString('o')
        composeProjectName = $PerfProjectName
        dataset = $Contract.dataset
        projectVerificationSkipped = [bool]$SkipProjectVerification
        environment = $Environment
        angularBuild = [pscustomobject]@{
            fileCount = $AngularBuild.fileCount
            totalBytes = $AngularBuild.totalBytes
        }
        preWarmSampleCount = $PreWarmSampleCount
        timing = [pscustomobject]@{
            preWarm = $PreWarmTiming
            postWarm = $PostWarmTiming
        }
        warmup = @($WarmupSummary)
    }
    if ($official) {
        $summary['officialBaseline'] = $PostWarmStatistics
    }
    else {
        $summary['diagnosticBaseline'] = $PostWarmStatistics
    }
    return [pscustomobject]$summary
}

function Protect-PerfArtifacts {
    param(
        [Parameter(Mandatory = $true)][string]$ArtifactDirectory,
        [string[]]$AdditionalSensitiveValues = @()
    )

    Assert-PerfArtifactPath -Path $ArtifactDirectory
    if (-not (Test-Path -LiteralPath $ArtifactDirectory -PathType Container)) {
        throw 'No existe el directorio de artefactos que debe sanearse.'
    }
    $secretNames = @(
        'POSTGRES_PASSWORD', 'JWT_SECRET_BASE64', 'RATE_LIMIT_KEY_SECRET_BASE64',
        'REDIS_PASSWORD', 'PGADMIN_DEFAULT_PASSWORD', 'BOOTSTRAP_ADMIN_PASSWORD',
        'E2E_PASSWORD', 'PERF_FIXTURE_USER_PASSWORD', 'PERF_PASSWORD'
    )
    $sensitive = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($name in $secretNames) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        if (-not [string]::IsNullOrWhiteSpace($value) -and $value.Length -ge 8) {
            [void]$sensitive.Add($value)
        }
    }
    foreach ($value in $AdditionalSensitiveValues) {
        if (-not [string]::IsNullOrWhiteSpace($value) -and $value.Length -ge 8) {
            [void]$sensitive.Add($value)
        }
    }

    $jwtPattern = '(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}(?![A-Za-z0-9_-])'
    $filesScanned = 0
    $filesRedacted = 0
    foreach ($file in @(Get-ChildItem -LiteralPath $ArtifactDirectory -Recurse -File)) {
        $filesScanned++
        $content = [IO.File]::ReadAllText($file.FullName)
        $sanitized = $content
        foreach ($value in $sensitive) {
            $sanitized = $sanitized.Replace($value, '[REDACTED]')
        }
        $sanitized = [regex]::Replace($sanitized, $jwtPattern, '[REDACTED_JWT]')
        if ($sanitized -cne $content) {
            [IO.File]::WriteAllText($file.FullName, $sanitized, [Text.UTF8Encoding]::new($false))
            $filesRedacted++
        }
    }

    foreach ($file in @(Get-ChildItem -LiteralPath $ArtifactDirectory -Recurse -File)) {
        $content = [IO.File]::ReadAllText($file.FullName)
        foreach ($value in $sensitive) {
            if ($content.Contains($value)) {
                throw 'La sanitización detectó un secreto residual en la evidencia.'
            }
        }
        if ([regex]::IsMatch($content, $jwtPattern)) {
            throw 'La sanitización detectó un JWT residual en la evidencia.'
        }
    }
    return [pscustomobject]@{
        filesScanned = $filesScanned
        filesRedacted = $filesRedacted
        completedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
}

function Remove-PerfArtifactDirectorySafely {
    param(
        [Parameter(Mandatory = $true)][string]$ArtifactDirectory,
        [scriptblock]$PathInspector
    )

    $candidate = Assert-PerfBaselineRunDirectory `
        -ArtifactDirectory $ArtifactDirectory `
        -PathInspector $PathInspector
    # Revalidación inmediata para reducir la ventana entre comprobación y borrado.
    $candidate = Assert-PerfBaselineRunDirectory `
        -ArtifactDirectory $candidate `
        -PathInspector $PathInspector
    [IO.Directory]::Delete($candidate, $true)
    if (Test-Path -LiteralPath $candidate) {
        throw 'No fue posible eliminar la evidencia insegura.'
    }
}

function Move-PerfArtifactDirectoryToQuarantine {
    param(
        [Parameter(Mandatory = $true)][string]$ArtifactDirectory,
        [scriptblock]$PathInspector
    )

    $candidate = Assert-PerfBaselineRunDirectory `
        -ArtifactDirectory $ArtifactDirectory `
        -PathInspector $PathInspector
    $allowedRoot = [IO.Path]::GetFullPath($PerfArtifactsDirectory).TrimEnd('\')
    $quarantineRoot = [IO.Path]::GetFullPath(
        (Join-Path $allowedRoot 'quarantine')
    ).TrimEnd('\')
    Assert-PerfPathComponentsSafe -AllowedRoot $allowedRoot `
        -TargetPath $quarantineRoot -PathInspector $PathInspector
    [void][IO.Directory]::CreateDirectory($quarantineRoot)
    Assert-PerfPathComponentsSafe -AllowedRoot $allowedRoot `
        -TargetPath $quarantineRoot -PathInspector $PathInspector

    $runName = Split-Path -Leaf $candidate
    if ($runName -cnotmatch $script:PerfBaselineRunNamePattern) {
        throw 'El nombre de corrida no es válido para cuarentena.'
    }
    $destination = [IO.Path]::GetFullPath(
        (Join-Path $quarantineRoot $runName)
    ).TrimEnd('\')
    if (-not ([IO.Path]::GetDirectoryName($destination)).Equals(
        $quarantineRoot,
        [StringComparison]::OrdinalIgnoreCase
    ) -or (Split-Path -Leaf $destination) -cne $runName) {
        throw 'El destino de cuarentena no es canónico.'
    }
    if (Test-Path -LiteralPath $destination) {
        throw 'El destino canónico de cuarentena ya existe.'
    }
    Assert-PerfPathComponentsSafe -AllowedRoot $allowedRoot `
        -TargetPath $destination -PathInspector $PathInspector

    # Última comprobación de origen, parent y destino antes de mover.
    $candidate = Assert-PerfBaselineRunDirectory `
        -ArtifactDirectory $candidate -PathInspector $PathInspector
    Assert-PerfPathComponentsSafe -AllowedRoot $allowedRoot `
        -TargetPath $quarantineRoot -PathInspector $PathInspector
    Assert-PerfPathComponentsSafe -AllowedRoot $allowedRoot `
        -TargetPath $destination -PathInspector $PathInspector
    if (Test-Path -LiteralPath $destination) {
        throw 'El destino de cuarentena cambió durante la validación.'
    }
    [IO.Directory]::Move($candidate, $destination)
    if ((Test-Path -LiteralPath $candidate) -or
        -not (Test-Path -LiteralPath $destination -PathType Container)) {
        throw 'La cuarentena de evidencia no pudo confirmarse.'
    }
    Assert-PerfPathComponentsSafe -AllowedRoot $allowedRoot `
        -TargetPath $quarantineRoot -PathInspector $PathInspector
    Assert-PerfPathComponentsSafe -AllowedRoot $allowedRoot `
        -TargetPath $destination -PathInspector $PathInspector
    Assert-PerfRunMarker -RunDirectory $destination
    return $destination
}

function Protect-PerfFailedRunArtifacts {
    param(
        [Parameter(Mandatory = $true)][string]$ArtifactDirectory,
        [string[]]$AdditionalSensitiveValues = @(),
        [scriptblock]$Protector
    )

    if ($null -eq $Protector) {
        $Protector = {
            param($Directory, $Values)
            Protect-PerfArtifacts `
                -ArtifactDirectory $Directory `
                -AdditionalSensitiveValues $Values
        }
    }
    try {
        return & $Protector $ArtifactDirectory $AdditionalSensitiveValues
    }
    catch {
        $quarantined = $false
        $quarantineFailed = $false
        try {
            [void](Move-PerfArtifactDirectoryToQuarantine `
                -ArtifactDirectory $ArtifactDirectory)
            $quarantined = $true
        }
        catch { $quarantineFailed = $true }
        if ($quarantined) {
            throw 'Falló la sanitización; la evidencia insegura quedó aislada en cuarentena.'
        }
        if (-not $quarantineFailed) {
            throw 'La protección fail-closed terminó en un estado inconsistente.'
        }
        try {
            Remove-PerfArtifactDirectorySafely -ArtifactDirectory $ArtifactDirectory
        }
        catch {
            throw 'Falló la sanitización y la evidencia insegura no pudo ponerse en cuarentena ni eliminarse.'
        }
        throw 'Falló la sanitización de la corrida; la evidencia insegura fue eliminada.'
    }
}

function Write-PerfBaselineMarkdown {
    param(
        [Parameter(Mandatory = $true)]$Summary,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $baselineProperty = if ($Summary.official) { 'officialBaseline' } else { 'diagnosticBaseline' }
    $baseline = $Summary.$baselineProperty
    $backendDocker = $baseline.docker.backend
    $postgres = $baseline.postgres
    $kind = if ($Summary.official) { 'oficial' } else { 'diagnóstica (no oficial)' }
    $cpuDescriptions = @($Summary.environment.cpu | ForEach-Object {
        "$($_.Name) ($($_.NumberOfCores) núcleos/$($_.NumberOfLogicalProcessors) hilos)"
    }) -join '; '
    $memoryGiB = [Math]::Round([double]$Summary.environment.totalPhysicalMemoryBytes / 1GB, 2)
    $lines = @(
        '# Línea Base Técnica',
        '',
        "> Estado: $($Summary.status)",
        "> Ejecución: $kind",
        "> Ventana postWarm: $($baseline.sampleCount) muestras",
        '',
        '## Identificación',
        '',
        "- Revisión Git: ``$($Summary.environment.gitRevision)``",
        "- Inicio UTC: ``$($Summary.startedAt)``",
        "- Fin UTC: ``$($Summary.completedAt)``",
        "- Dataset: ``$($Summary.dataset)``",
        "- Verificación de suites omitida: ``$($Summary.projectVerificationSkipped)``",
        "- Sistema operativo: ``$($Summary.environment.operatingSystem)``",
        "- CPU: ``$cpuDescriptions``",
        "- Memoria física: ``$memoryGiB GiB``",
        "- Java: ``$($Summary.environment.java)``",
        "- Node.js: ``$($Summary.environment.node)``",
        "- npm: ``$($Summary.environment.npm)``",
        "- Docker: ``$($Summary.environment.docker)``",
        "- Docker Compose: ``$($Summary.environment.dockerCompose)``",
        '',
        '## Medianas post-warm',
        '',
        "- CPU backend: $([Math]::Round([double]$backendDocker.cpuPercent.median, 3)) %",
        "- Memoria backend: $([Math]::Round([double]$backendDocker.memoryUsedBytes.median / 1MB, 3)) MiB",
        "- Sesiones PostgreSQL: $([Math]::Round([double]$postgres.numBackends.median, 3))",
        "- Sesiones en espera: $([Math]::Round([double]$postgres.waitingSessions.median, 3))",
        "- Locks en espera: $([Math]::Round([double]$postgres.waitingLocks.median, 3))",
        '',
        '## Evidencia',
        '',
        '- `baseline-summary.json`: resumen estructurado y medianas.',
        '- `preWarm/` y `postWarm/`: series JSONL, temporización y scrapes Prometheus saneados.',
        '- `warmup-requests.json`: 20 solicitudes seriales por endpoint crítico.',
        '- `angular-build.json`: tamaños y hashes del build de producción.',
        '- `verification/`: salida de suites y build, cuando no se omiten.',
        '',
        $(if ($Summary.official) {
            'Las cifras describen únicamente este equipo y el entorno Docker local aislado; no son una estimación directa de capacidad productiva.'
        } else {
            'Resultado diagnóstico: al omitirse las suites, estas cifras no pueden citarse como baseline oficial.'
        })
    )
    [IO.File]::WriteAllLines($Path, $lines, [Text.UTF8Encoding]::new($false))
}
