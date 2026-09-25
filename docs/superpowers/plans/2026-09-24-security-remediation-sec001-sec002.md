# Remediación acotada de SEC-001 y SEC-002

Fecha: 2026-09-24. Alcance aprobado: los dos hallazgos de concurrencia validados en `2026-08-30-fullstack-stress-security-analysis.md`; SEC-003 a SEC-005 permanecen fuera de esta corrección.

## Invariantes

- SEC-001: un conteo DRAFT se confirma una sola vez; sólo esa confirmación ajusta existencias y genera como máximo un movimiento de conteo. Una confirmación posterior recibe el error de negocio existente.
- SEC-002: un pago CASH o su movimiento compensatorio sólo se inserta mientras la sesión de la misma sucursal permanece OPEN. Si el cierre gana, el pago se rechaza; si el pago gana, el cierre incorpora el movimiento al monto esperado. Un reintento idempotente conserva su respuesta anterior sin crear otro movimiento.

## Secuencia de implementación

1. Añadir pruebas de integración con PostgreSQL real para las carreras SEC-001 y SEC-002, más controles secuenciales legítimos. Ejecutarlas sobre el código actual y conservar el fallo que demuestra cada vulnerabilidad.
2. SEC-001: bloquear la fila de `inventory.inventory_counts` antes de leer DRAFT e ítems; aplicar el ajuste dentro de la transacción existente y condicionar la transición final a DRAFT. Revisar llamadas directas y cualquier vía alternativa que confirme el mismo conteo.
3. SEC-002: sustituir la lectura agregada sin bloqueo de `ensureOpenCashSession` por una lectura de la fila de `cash.cash_sessions` con `FOR UPDATE OF cs`, validar estado y sucursal después de obtener el bloqueo, y mantenerlo hasta el commit de la transacción existente. Revisar cancelación de pago, devolución de venta en efectivo y cierre; la devolución comparte la misma frontera y requiere el mismo bloqueo.
4. Ejecutar primero las pruebas enfocadas, luego la suite backend completa y los casos ofensivos/controles en `tienda-perf`. Revisar el diff y hacer una pasada independiente de bypass/regresión por cada hallazgo.
5. Repetir Load por los cambios transaccionales, comparar con la línea base y registrar resultados y límites. Mantener los cambios de cada hallazgo en commits separados y actualizar el informe principal.

No se usarán servicios ni datos productivos. El entorno `tienda-perf` se eliminará al cerrar la validación, conservando únicamente los artefactos locales de prueba.
