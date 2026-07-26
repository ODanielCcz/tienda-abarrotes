# Alcance De La Base De Datos

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | TDA-REQ-BD-001 |
| Versión | v0.1 |
| Estado | Borrador |
| Responsable | Por definir |
| Fecha | 2026-07-12 |
| Clasificación | Interno |

## Control De Versiones

| Versión | Fecha | Autor | Cambio | Estado |
|---|---|---|---|---|
| v0.1 | 2026-07-12 | Equipo del proyecto | Creación inicial | Borrador |

## Objetivo

Definir la plataforma de datos de una tienda de abarrotes con operación física y en línea, múltiples sucursales, aplicaciones web y móvil, inventario por lotes, crédito, preparación fiscal mexicana y trabajo offline controlado.

## Alcance Incluido

| Código | Capacidad | Criterio Principal |
|---|---|---|
| RF-BD-001 | Identidad y acceso | Usuarios de negocio, roles y permisos sin almacenar contraseñas en texto plano. |
| RF-BD-002 | Catálogo | Productos separados de sus presentaciones, códigos de barras, marcas, categorías, unidades, precios e impuestos. |
| RF-BD-003 | Inventario | Existencia por almacén, lotes, caducidad, reservas, conteos y movimientos trazables. |
| RF-BD-004 | Compras | Proveedores, recepciones, costos y cuentas por pagar. |
| RF-BD-005 | Ventas | POS, web y móvil, pagos, devoluciones y reservas. |
| RF-BD-006 | Caja | Apertura, cierre, ingresos, retiros y diferencias. |
| RF-BD-007 | Crédito | Límites, cuentas por cobrar, cargos y abonos. |
| RF-BD-008 | Facturación | Datos y estados para CFDI 4.0; timbrado queda fuera de esta fase. |
| RF-BD-009 | Offline | Cupos de inventario por dispositivo e idempotencia al sincronizar. |
| RF-BD-010 | Auditoría | Eventos funcionales append-only y bitácora administrativa de PostgreSQL. |

## Reglas De Negocio

| Código | Regla |
|---|---|
| RN-001 | La existencia disponible es física menos reservada menos asignada a dispositivos. |
| RN-002 | El inventario disponible nunca puede ser negativo. |
| RN-003 | Una operación multirenglón se confirma completa o se revierte completa. |
| RN-004 | El costo se calcula mediante promedio ponderado. |
| RN-005 | La salida física sugerida de lotes sigue FEFO. |
| RN-006 | Una operación móvil repetida con la misma clave de idempotencia no duplica efectos. |
| RN-007 | Los clientes offline no confirman compras ni pagos; solo preparan carritos. |
| RN-008 | Los empleados offline venden únicamente contra cupos asignados. |
| RN-009 | Ventas, compras, movimientos y auditorías no se eliminan. |
| RN-010 | Las bitácoras nunca guardan contraseñas, tokens ni datos completos de pago. |

## Fuera De Alcance

- Backend Spring Boot, web Angular y aplicación móvil.
- Integración con PAC, timbrado, cancelación SAT o descarga masiva.
- Pasarela de pago real.
- Docker de producción, alta disponibilidad o réplica.
- Contabilidad financiera completa.

