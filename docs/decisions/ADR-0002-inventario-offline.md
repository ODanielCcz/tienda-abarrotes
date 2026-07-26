# ADR-0002: Inventario Offline Mediante Cupos Por Dispositivo

Date: 2026-07-12
Status: Accepted

## Context

Un dispositivo desconectado no puede bloquear el saldo central y podría provocar sobreventa.

## Decision

Asignar cupos explícitos por dispositivo, almacén, presentación y lote. Los empleados solo venden offline contra ese cupo; los clientes requieren conexión para confirmar y pagar.

## Consequences

La disponibilidad central descuenta los cupos asignados. La sincronización debe ser idempotente y conciliar consumos, devoluciones y conflictos.

