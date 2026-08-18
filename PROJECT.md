# Ficha Del Proyecto

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Estado | Backend MVP implementado; en validación para RC1 |
| Responsable | Por definir |
| Tipo | Fullstack con cliente web y móvil |
| Sensibilidad | Interno |
| Fecha de inicio | 2026-07-12 |
| Última revisión | 2026-08-18 |

## Objetivo Actual

Cerrar y validar el backend modular de la tienda: identidad, catálogo, organización, inventario, compras, ventas, caja, clientes, reportes, facturación interna, sincronización y auditoría.

## Tecnologías Aprobadas

- PostgreSQL 18.4.
- pgAudit 18.0.
- Flyway 12.11.0.
- pgAdmin 4 9.16.
- Docker Compose.
- Java 21.
- Spring Boot 4.1.0.
- Gradle Kotlin DSL con Gradle Wrapper.

## Decisiones Vigentes

- Preparar el modelo para varias sucursales.
- Usar UUID, idempotencia, secuencias y conflictos para clientes offline.
- Usar promedio ponderado para costeo y FEFO para salida física de lotes.
- Mantener ventas y pagos offline fuera del MVP.
- Mantener auditoría funcional append-only y auditoría técnica en pgAudit.
- Preparar documentos fiscales internos sin timbrado ni PAC.
- Usar arquitectura hexagonal modular con dependencias dirigidas hacia dominio y aplicación.
- Proteger la API mediante JWT y permisos por caso de uso.
- Mantener correlación, métricas Prometheus, trazas OpenTelemetry y logs estructurados.
- Mantener secretos reales únicamente en archivos locales ignorados.
- Exigir autorización por sucursal, precios resueltos por servidor e idempotencia atómica en operaciones sensibles.
- Exigir vínculo explícito usuario-dispositivo para Sync v1.

## Próximas Acciones

1. Ejecutar validación aislada completa desde migración V001 hasta V034.
2. Provisionar y revisar los vínculos locales usuario-dispositivo de Sync antes de usar endpoints offline.
3. Activar el workflow remoto Backend CI y verificar una ejecución verde sobre `main`.
4. Aprobar y etiquetar el candidato `backend-v1.0.0-rc1`.
5. Definir retención de auditoría, outbox, logs y trazas; después priorizar el backlog v2.
