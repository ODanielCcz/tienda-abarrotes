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

- Autenticación y seguridad
- Usuarios, roles y permisos base
- Auditoría funcional
- Catálogo
  - Marcas
  - Categorías
  - Árbol de categorías
  - Productos
  - Presentaciones
- Inventario
  - Stock
  - Lotes
  - Pallets
  - Movimientos
  - Recepción de mercancía
- Compras
  - Proveedores
  - Órdenes de compra
  - Confirmación
  - Recepción conectada a inventario
- Ventas
  - Creación de venta
  - Descuento real de stock
  - FEFO para lotes
  - Idempotencia
  - Cancelación y reposición de stock

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
.\scripts\db-up.ps1
.\scripts\db-migrate.ps1
.\scripts\db-seed.ps1

Set-Location .\apps\backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local" --no-daemon
```

También se puede levantar con Docker Compose:

```powershell
docker compose up -d database backend
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
- `docs/operations`: guías de operación local.
- `deploy`: archivos Docker.
- `scripts`: comandos PowerShell repetibles.

## Decisiones técnicas

- Flyway es el único responsable de aplicar cambios estructurales de base de datos.
- El backend coordina las operaciones comerciales; la base protege invariantes con restricciones y transacciones.
- Las cuentas PostgreSQL y los usuarios de negocio son conceptos separados.
- El backend se organiza por módulos de negocio con separación de dominio, aplicación, puertos y adaptadores.
- No se deben versionar secretos ni datos productivos. Usa `.env.example` como plantilla local.
