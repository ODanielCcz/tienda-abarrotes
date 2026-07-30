# ADR-0007: Idempotencia, Secuencias Y Conflictos Offline

## Estado

Aceptada.

## Contexto

Los dispositivos móviles pueden perder conectividad, reenviar operaciones o entregarlas fuera de orden. El backend debe evitar efectos duplicados y proteger el aislamiento entre sucursales.

## Decisión

El protocolo Sync usa inbox, outbox y checkpoints por dispositivo.

- Cada envelope tiene `operationId`, `idempotencyKey`, `deviceSequence` y un hash SHA-256 canónico.
- Misma clave y mismo hash devuelve el resultado persistido.
- Misma clave con contenido distinto produce `SYNC_IDEMPOTENCY_CONFLICT`.
- Solo se procesa la secuencia contigua esperada.
- Los huecos y secuencias antiguas crean conflictos controlados.
- El outbox usa una secuencia global incremental y filtra eventos por sucursal.
- El usuario autenticado debe tener acceso a la sucursal del dispositivo.
- `CLIENT_WINS` y `MERGED` quedan limitados a carritos; los conteos usan `SERVER_WINS` o `REJECTED`.

En v1 solo se admiten `CART_UPSERT` e `INVENTORY_COUNT_CREATE` para dispositivos `MOBILE_EMPLOYEE`.

## Consecuencias

- Los reintentos de red son seguros.
- Una operación fuera de orden no avanza el checkpoint.
- Los conflictos quedan trazables y se resuelven una sola vez.
- Ventas, pagos, ajustes y confirmación de conteos continúan siendo exclusivamente online.
