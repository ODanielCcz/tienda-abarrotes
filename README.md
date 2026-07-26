# Tienda de Abarrotes

Repositorio monorepo para el sistema de gestión de una tienda de abarrotes. Actualmente incluye la plataforma de datos PostgreSQL y un backend Spring Boot con administración de marcas y fundación transversal de seguridad, auditoría y observabilidad. Las aplicaciones Angular y móvil permanecen vacías.

## Componentes Disponibles

- PostgreSQL 18 con pgAudit.
- Migraciones SQL versionadas con Flyway.
- pgAdmin opcional.
- Seeds ficticios para desarrollo.
- Pruebas SQL de integridad, transacciones y auditoría.
- Scripts PowerShell para operación local.
- Backend Java 21 y Spring Boot con arquitectura hexagonal modular.
- Marcas con paginación, filtros, actualización y cambio de estado auditable.
- Login JWT, BCrypt, roles, permisos y respuestas 401/403 uniformes.
- OpenAPI/Swagger, CORS configurable, Prometheus y OpenTelemetry.

## Inicio Rápido

```powershell
Copy-Item .env.example .env
.\scripts\db-up.ps1
.\scripts\db-migrate.ps1
.\scripts\db-seed.ps1
.\scripts\db-test.ps1

Set-Location .\apps\backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local" --no-daemon
```

Para abrir pgAdmin:

```powershell
.\scripts\db-tools.ps1
```

La operación de datos está en `docs/operations/database-docker.md` y el contrato del backend en `docs/api/backend-api-v1.md`. No almacenes secretos ni datos productivos en este repositorio.

## Rutas Principales

- `apps/backend`: backend Spring Boot con identidad, marcas y capacidades transversales.
- `apps/web`: futura aplicación Angular.
- `apps/mobile`: futura aplicación móvil.
- `database/migrations`: fuente canónica del esquema.
- `database/seeds`: datos ficticios y catálogos de desarrollo.
- `database/tests`: pruebas de la base.
- `database/admin`: scripts controlados para cuentas PostgreSQL.
- `docs/architecture`: modelo y diccionario de datos.
- `deploy/postgres`: imagen PostgreSQL con pgAudit.

## Decisiones Técnicas

- La aplicación futura usará Hibernate solo para validar el esquema (`ddl-auto=validate`).
- Flyway será el único responsable de aplicar cambios estructurales.
- Las operaciones comerciales se coordinarán en el backend; la base protege invariantes con restricciones y transacciones.
- Las cuentas PostgreSQL y los usuarios de negocio son conceptos separados.
- El backend se organizará por módulos de negocio; cada módulo separará dominio, aplicación, puertos y adaptadores.
