# Diccionario De Datos

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | TDA-DIC-BD-001 |
| Versión | v0.1 |
| Estado | Borrador |
| Responsable | Por definir |
| Fecha | 2026-07-12 |
| Clasificación | Interno |

## Convenciones De Columnas

| Convención | Definición |
|---|---|
| `*_id` | UUID de entidad relacionada. |
| `created_at` | Fecha UTC de creación. |
| `updated_at` | Fecha UTC de última modificación. |
| `version` | Entero para control optimista. |
| `status` | Estado restringido mediante `CHECK`. |
| `*_snapshot` | Valor histórico que no depende del catálogo vigente. |
| `metadata` | JSONB sin secretos ni información de pago completa. |

## Tablas Principales

| Esquema.Tabla | Propósito | Claves E Invariantes |
|---|---|---|
| organization.branches | Sucursales | Código único y zona horaria obligatoria. |
| organization.warehouses | Almacenes | Código único por sucursal. |
| organization.devices | Dispositivos móviles/POS | Identificador único y estado controlado. |
| iam.users | Usuarios de negocio | Nombre y correo únicos; hash obligatorio. |
| iam.roles / permissions | Autorización | Códigos únicos y relaciones N:M. |
| iam.database_principals | Inventario de roles PostgreSQL | Nombre de rol único, propósito y responsable. |
| catalog.products | Producto conceptual | Categoría y marca opcionales. |
| catalog.product_presentations | Unidad vendible | SKU único, unidad y factor de conversión. |
| catalog.barcodes | Códigos escaneables | Código globalmente único. |
| inventory.stock_balances | Saldo por almacén/presentación | Física >= reservada + asignada. |
| inventory.lots | Lotes y caducidad | Lote único por presentación/proveedor. |
| inventory.stock_movements | Encabezado de movimiento | Documento inmutable con origen y estado. |
| inventory.reservations | Reserva temporal | Expiración, estado y clave idempotente. |
| inventory.device_stock_allocations | Cupo offline | Consumido + devuelto <= asignado. |
| purchasing.purchases | Compra | Folio único por proveedor y sucursal. |
| purchasing.accounts_payable | Saldo por pagar | Original >= saldo >= 0. |
| sales.customers | Cliente | Cliente general o registrado. |
| sales.sales_orders | Venta/pedido | Canal POS, WEB o MOBILE e idempotencia. |
| sales.payments | Pago | Estado y referencia de proveedor. |
| sales.credit_accounts | Crédito del cliente | Límite y estado controlados. |
| cash.cash_sessions | Apertura y cierre | Solo una sesión abierta por caja. |
| billing.fiscal_documents | CFDI preparado | Versión 4.0, estado y UUID fiscal opcional. |
| sync.inbox_operations | Operaciones recibidas | Idempotencia y secuencia únicas. |
| audit.business_events | Eventos de negocio | Append-only y sin secretos. |
| audit.database_principal_events | Cambios de cuentas BD | Antes/después y ejecutor. |

El detalle completo de columnas, restricciones e índices está en las migraciones versionadas, que constituyen la especificación ejecutable del esquema.

