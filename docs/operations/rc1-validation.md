# Validación Repetible De Backend RC1

Esta guía valida el backend en una base aislada. No usa `Flyway clean` ni los volúmenes de desarrollo.

## 1. Prerrequisitos

- Docker Desktop con motor Linux iniciado.
- Java 21 disponible para Gradle.
- El archivo `.env` local existente permanece sin cambios.

## 2. Validar Código

```powershell
Set-Location 'C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes\apps\backend'
.\gradlew.bat test --no-daemon
.\gradlew.bat bootJar --no-daemon
```

## 3. Crear Entorno Aislado

```powershell
Set-Location 'C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes'
$rc1Env = Join-Path $env:TEMP 'tienda-rc1.env'
$rc1Project = 'tienda-abarrotes-rc1'
@"
POSTGRES_DB=tienda_rc1
POSTGRES_USER=tienda_owner
POSTGRES_PASSWORD=$([guid]::NewGuid().ToString('N'))
POSTGRES_PORT=55433
BACKEND_PORT=58080
JWT_SECRET_BASE64=$([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48)))
BOOTSTRAP_ADMIN_ENABLED=false
PGADMIN_DEFAULT_PASSWORD=$([guid]::NewGuid().ToString('N'))
"@ | Set-Content -LiteralPath $rc1Env -NoNewline
```

## 4. Aplicar Y Verificar Migraciones

```powershell
docker compose --env-file $rc1Env -p $rc1Project up -d database
docker compose --env-file $rc1Env -p $rc1Project --profile migrate run --rm flyway migrate
docker compose --env-file $rc1Env -p $rc1Project --profile migrate run --rm flyway validate
docker compose --env-file $rc1Env -p $rc1Project exec -T database `
  psql -U tienda_owner -d tienda_rc1 -Atc `
  "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;"
```

El último valor debe ser `034`.

## 5. Imagen Y Smoke Test

```powershell
docker compose --env-file $rc1Env -p $rc1Project build backend
docker compose --env-file $rc1Env -p $rc1Project up -d backend
Invoke-RestMethod 'http://localhost:58080/actuator/health'
```

La respuesta debe contener `status` igual a `UP`.

## 6. Limpieza Del Entorno Temporal

```powershell
docker compose --env-file $rc1Env -p $rc1Project down -v
Remove-Item -LiteralPath $rc1Env -Force
```

Esta limpieza elimina exclusivamente el proyecto Docker temporal `tienda-abarrotes-rc1`.
