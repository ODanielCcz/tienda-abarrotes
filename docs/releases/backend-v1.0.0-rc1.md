# Backend v1.0.0 RC1

## Alcance

El candidato incluye autenticación, identidad, organización, catálogo, inventario, compras, ventas, caja, clientes, devoluciones, reportes v2, facturación interna y Sync offline v1.

## Facturación V1

- Perfiles emisores por sucursal.
- Perfiles fiscales de clientes.
- Clasificación SAT de productos y unidades.
- Documentos internos `INCOME` con snapshots.
- Transición explícita `DRAFT` a `READY`.
- Sin XML, timbrado ni PAC.

## Sync Offline V1

- Inbox con idempotencia SHA-256 y secuencia por dispositivo.
- Operaciones `CART_UPSERT` e `INVENTORY_COUNT_CREATE`.
- Outbox filtrado por sucursal.
- Checkpoints de recepción y confirmación.
- Conflictos con resolución controlada.
- Aislamiento por usuario, sucursal y dispositivo.

## Evidencia De Validación

- Migraciones Flyway V001-V034 aplicadas en PostgreSQL/Testcontainers y validadas desde una base aislada.
- Suite Gradle completa y ArchUnit en verde.
- `bootJar` generado correctamente.
- PostgreSQL y backend Docker en estado healthy.
- Smoke fiscal: venta pagada, documento `DRAFT`, transición `READY` y snapshots.
- Smoke Sync: carrito, conteo, reintento idempotente, hueco de secuencia, resolución, outbox y checkpoint.
- `EXPLAIN ANALYZE` revisado para documentos, inbox, outbox y conflictos.
- Colección Postman y environment sin secretos.

## Seguridad

- JWT sin claims de permisos duplicados.
- Secretos locales obligatorios mediante `.env` ignorado.
- Sin claves o contraseñas reales en archivos versionados.
- Permisos específicos por endpoint.
- Respuestas con `reason` y `correlationId`.
- Autorización por sucursal centralizada; `SYSTEM_ADMIN` conserva acceso global.
- Precio de venta resuelto por el servidor y validación de precio esperado.
- Vínculo obligatorio y único entre dispositivo Sync y usuario responsable.
- Límites de payload y tasa para operaciones Sync.

## Criterios Para Etiquetar

- La suite Gradle, `bootJar`, Flyway aislado, Docker health y Trivy deben estar en verde.
- El workflow remoto `Backend CI` debe terminar correctamente en el commit candidato.
- Los dispositivos Sync que vayan a usarse deben estar vinculados mediante el procedimiento operativo versionado.
- La etiqueta anotada será `backend-v1.0.0-rc1`; no se crea antes de cumplir estos criterios.

## Backlog V2

- Timbrado CFDI, PAC, cancelación SAT y notas de crédito fiscales.
- Autenticación propia de dispositivos para clientes.
- Ventas, pagos y cupos de inventario offline.
- Crédito a clientes y cuentas por pagar.
- Listas de precios y códigos de barras avanzados.
- Exportaciones PDF/Excel.
- Angular web y aplicación móvil.
