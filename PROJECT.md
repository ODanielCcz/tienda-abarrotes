# Ficha Del Proyecto

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Estado | Activo - marcas y fundación transversal del backend implementadas |
| Responsable | Por definir |
| Tipo | Fullstack con cliente web y móvil |
| Sensibilidad | Interno |
| Fecha de inicio | 2026-07-12 |
| Última revisión | 2026-07-25 |

## Objetivo Actual

Construir la plataforma de datos y el backend modular para usuarios, catálogo, inventario, compras, ventas, caja, crédito, facturación, sincronización y auditoría.

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
- Usar UUID y operaciones idempotentes para clientes móviles.
- Usar promedio ponderado para costeo y FEFO para salida física de lotes.
- Permitir ventas offline de empleados únicamente contra cupos por dispositivo.
- Mantener auditoría funcional en tablas append-only y auditoría técnica en pgAudit.
- Preparar CFDI 4.0 sin implementar timbrado ni conexión con PAC.
- Usar arquitectura hexagonal modular en el backend con dependencias dirigidas hacia el dominio.
- Proteger la API mediante JWT y permisos por caso de uso.
- Mantener correlación, métricas Prometheus, trazas OpenTelemetry y logs estructurados.

## Próximas Acciones

1. Revisar el modelo con el responsable del negocio.
2. Aprobar el alcance de la versión 1.0 de la base.
3. Definir la siguiente vertical entre organización y catálogo de productos.
4. Dockerizar el backend y preparar CI/CD antes de un entorno compartido.
5. Definir retención de auditoría, logs y trazas.
