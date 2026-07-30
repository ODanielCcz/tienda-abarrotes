# Ficha Del Proyecto

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Estado | Backend MVP implementado; en validación para RC1 |
| Responsable | Por definir |
| Tipo | Fullstack con cliente web y móvil |
| Sensibilidad | Interno |
| Fecha de inicio | 2026-07-12 |
| Última revisión | 2026-07-29 |

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

## Próximas Acciones

1. Ejecutar validación completa desde migración V001 hasta V031.
2. Aprobar y etiquetar el candidato `backend-v1.0.0-rc1`.
3. Definir CI/CD y un entorno compartido.
4. Definir retención de auditoría, outbox, logs y trazas.
5. Priorizar backlog v2: PAC/CFDI, ventas offline, crédito, exportaciones y clientes web/móvil.
