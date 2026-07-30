# Tienda de Abarrotes

Sistema modular para administrar una tienda de abarrotes. El repositorio está preparado como monorepo para backend, futura web Angular y futura app móvil.

## Stack

- Java 21
- Spring Boot
- Gradle Kotlin DSL
- PostgreSQL
- Flyway
- Docker Compose
- JWT
- Arquitectura hexagonal
- Clean Architecture

## Módulos actuales

- Autenticación, usuarios, roles, permisos y acceso por sucursal
- Auditoría funcional
- Catálogo: marcas, categorías, productos y presentaciones
- Inventario: stock, lotes, pallets, movimientos, recepción y operación avanzada
- Compras: proveedores, órdenes, confirmación y recepción
- Ventas: stock concurrente, FEFO, idempotencia, pagos, caja, clientes y devoluciones
- Organización: sucursales, almacenes, cajas y dispositivos
- Reportes v2: ventas, margen, rentabilidad, inventario, caducidad y devoluciones
- Facturación interna:
  - perfiles emisores y receptores;
  - clasificación SAT de productos y unidades;
  - documentos `DRAFT` y `READY` con snapshots inmutables.
- Sincronización offline v1:
  - inbox idempotente;
  - outbox por sucursal;
  - checkpoints y conflictos;
  - conteos físicos y carritos offline.

## Componentes disponibles

- PostgreSQL 18 con pgAudit.
- Migraciones SQL versionadas con Flyway.
- pgAdmin opcional.
- Seeds ficticios para desarrollo.
- Pruebas SQL de integridad, transacciones y auditoría.
- Scripts PowerShell para operación local.
- Backend Spring Boot con arquitectura hexagonal modular.
- OpenAPI/Swagger con modo oscuro.
- CORS configurable.
- Actuator, Prometheus y OpenTelemetry.

## Inicio rápido

```powershell
Copy-Item .env.example .env
```

Reemplaza contraseñas y genera `JWT_SECRET_BASE64`:

```powershell
[Convert]::ToBase64String(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)
```

Después:

```powershell
.\scripts\db-up.ps1
.\scripts\db-migrate.ps1
.\scripts\db-seed.ps1

Set-Location .\apps\backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local" --no-daemon
```

También se puede levantar con Docker Compose:

```powershell
docker compose up -d database
docker compose --profile migrate run --rm flyway migrate
docker compose build backend
docker compose up -d backend
```

Swagger:

```text
http://localhost:8080/swagger-ui/index.html
```

Health:

```text
http://localhost:8080/actuator/health
```

## Rutas principales

- `apps/backend`: backend Spring Boot.
- `apps/web`: futura aplicación Angular.
- `apps/mobile`: futura aplicación móvil.
- `database/migrations`: fuente canónica del esquema.
- `database/seeds`: datos ficticios y catálogos de desarrollo.
- `database/tests`: pruebas de la base.
- `database/admin`: scripts controlados para cuentas PostgreSQL.
- `docs/architecture`: modelo, diccionario y decisiones técnicas.
- `docs/api`: documentación de endpoints.
- `docs/api/postman`: colección y environment local sin secretos.
- `docs/operations`: guías de operación local.
- `deploy`: archivos Docker.
- `scripts`: comandos PowerShell repetibles.

## Decisiones técnicas

- Flyway es el único responsable de aplicar cambios estructurales de base de datos.
- El backend coordina las operaciones comerciales; la base protege invariantes con restricciones y transacciones.
- Las cuentas PostgreSQL y los usuarios de negocio son conceptos separados.
- El backend se organiza por módulos con dominio, aplicación, puertos y adaptadores.
- Las transacciones de escritura pertenecen al caso de uso mediante `TransactionRunner`.
- Los documentos fiscales congelan snapshots y son inmutables después de `READY`.
- Sync v1 solo admite carritos y conteos; ventas, pagos y ajustes offline quedan fuera del MVP.
- No se versionan secretos ni datos productivos. Usa `.env.example` como plantilla local.

Estado objetivo actual: candidato `backend-v1.0.0-rc1`.
