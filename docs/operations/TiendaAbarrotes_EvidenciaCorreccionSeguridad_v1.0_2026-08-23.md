# Evidencia De Corrección De Seguridad Del Backend

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | ODCC-SEG-EVI-001 |
| Versión | v1.0 |
| Estado | Implementado y validado |
| Fecha | 2026-08-23 |
| Alcance | Backend, migraciones y pruebas |

## Resultado

Se corrigieron los ocho hallazgos documentados en `ODCC-SEG-PLAN-001`. No se modificaron Angular ni la aplicación móvil.

| ID | Corrección | Evidencia principal |
|---|---|---|
| SEG-001 | Reembolsos distribuidos por pago original; efectivo limitado a su porción capturada | `sales.refund_allocations` y prueba de pago mixto 10 CASH + 90 CARD |
| SEG-002 | Recepción de inventario devuelve `CREATED` o `REPLAYED`; compras solo actualiza cantidades para `CREATED` | Prueba de replay y prueba concurrente de idempotencia |
| SEG-003 | Venta reclama idempotencia con `ON CONFLICT DO NOTHING RETURNING` | Dos solicitudes concurrentes responden 201 con el mismo identificador y un solo descuento de stock |
| SEG-004 | Límite HTTP configurable y límites de colecciones transaccionales | 1 MiB general, 256 KiB Sync, límites de venta/compra/recepción/pallet |
| SEG-005 | Usuario inexistente ejecuta una comparación BCrypt contra hash ficticio | Prueba verifica exactamente una comparación criptográfica en ambos caminos |
| SEG-006 | Se eliminó el lockout persistente provocado por cinco fallos y se agregó limitación por IP, usuario/IP y cuenta | Pruebas de login y limitador en memoria |
| SEG-007 | Política central de 12 a 128 caracteres, contraseñas comunes y similitud con username | Creación, cambio de contraseña y bootstrap reutilizan `PasswordPolicyPort` |
| SEG-008 | `BranchScope` se aplica en PostgreSQL antes de `ORDER BY` y `LIMIT 200` | Prueba de consultas de ventas/compras e índices por sucursal y fecha |

## Migraciones

- `V035__sales_refund_allocations.sql`: trazabilidad y unicidad de asignaciones de reembolso.
- `V036__branch_scoped_listing_indexes.sql`: índices compuestos para ventas y compras por sucursal.
- Flyway validó 36 migraciones y aplicó `V035` y `V036`; la base local quedó en versión `v036`.

## Validación Ejecutada

```powershell
.\gradlew.bat test --tests com.odcc.tienda.architecture.HexagonalArchitectureTest --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat bootJar --no-daemon
docker compose --profile migrate run --rm flyway migrate
docker compose build backend
docker compose up -d backend
```

Resultados:

- ArchUnit: correcto.
- Suite completa: 272 pruebas, todas correctas.
- `bootJar`: correcto.
- Contenedor de PostgreSQL: saludable.
- Contenedor backend: saludable en `http://localhost:8080`.
- Prueba HTTP de 1 MiB + 1 byte: `413 REQUEST_PAYLOAD_TOO_LARGE` con `correlationId` preservado.
- `git diff --check`: sin errores de espacios.

## Decisiones Y Riesgos Residuales

- Los límites de login continúan en memoria y son apropiados para una sola instancia. Antes de escalar horizontalmente debe evaluarse Redis u otro almacén compartido.
- Los reembolsos no efectivos quedan trazados por asignación; la integración real con adquirentes de tarjeta continúa fuera del MVP.
- Los listados conservan el límite fijo de 200 registros, pero la autorización ahora se aplica antes del límite. La paginación por cursor queda como mejora posterior.
- `API_MAX_JSON_PAYLOAD_BYTES` permite ajustar el máximo general sin recompilar.
- No se detectó inyección SQL directa durante el barrido; las consultas nuevas usan parámetros nombrados.

## Trazabilidad

Plan relacionado: `TiendaAbarrotes_PlanCorreccionSeguridad_v0.1_2026-08-23.md`.
