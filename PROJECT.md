# Ficha Del Proyecto

| Campo           | Valor                                                            |
| --------------- | ---------------------------------------------------------------- |
| Proyecto        | Tienda de Abarrotes                                              |
| Estado          | Backend MVP y Angular Web MVP implementados y validados como RC1 |
| Responsable     | Por definir                                                      |
| Tipo            | Fullstack con cliente web y móvil                                |
| Sensibilidad    | Interno                                                          |
| Fecha de inicio | 2026-07-12                                                       |
| Última revisión | 2026-08-30                                                       |

## Objetivo Actual

Cerrar y validar el backend modular y el Angular Web MVP de la tienda: identidad, catálogo, organización, inventario, compras, ventas, caja, clientes, reportes, facturación interna, sincronización y auditoría.

## Tecnologías Aprobadas

- PostgreSQL 18.4.
- pgAudit 18.0.
- Flyway 12.11.0.
- pgAdmin 4 9.16.
- Docker Compose.
- Java 21.
- Spring Boot 4.1.0.
- Gradle Kotlin DSL con Gradle Wrapper.
- Redis 8.8.1 para protección distribuida del login en producción.
- Angular 22.1, Angular Material, TypeScript 6, SCSS, Vitest y Playwright.

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
- Exigir rate limiting Redis compartido y fail-closed en producción; conservar memoria únicamente para local/test.
- Derivar claves de rate limiting mediante HMAC con un secreto independiente del JWT.

## Próximas Acciones

1. Publicar la rama y ejecutar los checks remotos de backend y web.
2. Activar Gitleaks, Dependency Review, Dependency Graph, Dependabot alerts, Secret Scanning y Push Protection en GitHub.
3. Provisionar y revisar los vínculos usuario-dispositivo antes de usar Sync fuera del entorno local.
4. Ejecutar una ronda de aceptación funcional con perfiles reales de administrador, cajero y almacén.
5. Aprobar y etiquetar los candidatos `backend-v1.0.0-rc1` y `web-v1.0.0-rc1` cuando los checks remotos estén verdes.
