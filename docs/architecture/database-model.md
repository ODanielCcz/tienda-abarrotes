# Modelo De Datos

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | TDA-ARQ-BD-001 |
| Versión | v0.1 |
| Estado | Borrador |
| Responsable | Por definir |
| Fecha | 2026-07-12 |
| Clasificación | Interno |

## Criterios Arquitectónicos

- Una sola base PostgreSQL con esquemas funcionales.
- UUID para agregados sincronizables y referencias distribuidas.
- `TIMESTAMPTZ` en UTC y zona horaria configurada por sucursal.
- `NUMERIC(19,4)` para valores monetarios de cálculo y `NUMERIC(18,3)` para cantidades.
- Registros maestros desactivables; documentos transaccionales cancelables, nunca eliminables.
- Saldos actuales para lectura rápida y movimientos como historial inmutable.
- Flyway como única fuente de evolución del esquema.

## Esquemas

| Esquema | Responsabilidad |
|---|---|
| organization | Sucursales, almacenes y dispositivos. |
| iam | Usuarios, autorización y registro de cuentas PostgreSQL. |
| catalog | Productos, presentaciones, precios e impuestos. |
| inventory | Saldos, lotes, movimientos, reservas, cupos y conteos. |
| purchasing | Proveedores, compras y cuentas por pagar. |
| sales | Clientes, carritos, pedidos, pagos, devoluciones y crédito. |
| cash | Cajas, sesiones y movimientos de efectivo. |
| billing | Datos fiscales y comprobantes. |
| sync | Inbox, outbox, secuencias y conflictos móviles. |
| audit | Auditoría funcional, técnica y revisiones de acceso. |

## Concurrencia De Inventario

La asignación conectada de inventario se realizará con una actualización condicional dentro de la transacción:

```sql
UPDATE inventory.stock_balances
SET on_hand_quantity = on_hand_quantity - :quantity,
    version = version + 1
WHERE warehouse_id = :warehouse_id
  AND product_presentation_id = :presentation_id
  AND available_quantity >= :quantity;
```

Una fila afectada confirma que la operación obtuvo el inventario. Cero filas afectadas representa existencia insuficiente. Las operaciones con varios productos deberán revertirse si cualquier renglón falla.

## Auditoría

Las tablas del esquema `audit` son append-only. El rol de aplicación puede insertar eventos autorizados, pero no actualizarlos ni eliminarlos. pgAudit cubre cambios de roles, privilegios y DDL que no necesariamente pasan por las tablas funcionales.

