# Notas De Versión

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | TDA-REL-001 |
| Versión | v0.2 |
| Estado | Borrador |
| Responsable | Por definir |
| Fecha | 2026-07-25 |
| Clasificación | Interno |

## v0.1 - Plataforma De Datos Inicial

- Modelo PostgreSQL modular preparado para varias sucursales.
- Migraciones Flyway para catálogo, inventario, compras, ventas, caja, crédito, facturación y sincronización.
- Auditoría append-only e inventario de cuentas PostgreSQL.
- Imagen Docker PostgreSQL 18.4 con pgAudit 18.0.
- pgAdmin, seeds, pruebas y scripts PowerShell de operación.

## v0.2 - Marcas Y Fundación Transversal Del Backend

- Administración de marcas: alta, consulta, listado paginado, filtros, orden, actualización y cambio de estado sin borrado físico.
- Seguridad stateless con login, BCrypt, JWT, roles y permisos por operación.
- Migración V013 con permisos del catálogo y roles base.
- Auditoría funcional transaccional para marcas y bitácora de autenticación.
- Errores HTTP uniformes, correlación, CORS configurable y pruebas de arquitectura hexagonal.
- OpenAPI con esquema Bearer y respuestas controladas.
- Prometheus, trazas OpenTelemetry y logging estructurado de producción.
- Pruebas unitarias e integración con PostgreSQL real mediante Testcontainers.

## v0.3 - Categorias Del Catalogo

- Administracion de categorias: alta, consulta, listado paginado, filtros, orden, actualizacion y cambio de estado sin borrado fisico.
- Soporte de `parentCategoryId` para categorias hijas con validacion de padre existente y bloqueo de auto-parentesco.
- Migracion V014 con permisos `CATALOG_CATEGORY_*` para roles `SYSTEM_ADMIN` y `CATALOG_MANAGER`.
- Auditoria funcional transaccional para creacion, actualizacion y cambio de estado de categorias.
- Pruebas unitarias, persistencia PostgreSQL, API REST y OpenAPI para el modulo de categorias.