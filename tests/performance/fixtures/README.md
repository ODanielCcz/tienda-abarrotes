# Fixtures Deterministas De Rendimiento

Este directorio contiene datos exclusivamente sintéticos para el proyecto Compose aislado `tienda-perf`. No es una migración Flyway y no debe ejecutarse contra bases compartidas, de desarrollo habitual ni productivas.

## Orden De Carga

El orden obligatorio en una base efímera nueva es:

1. Iniciar `database` y `redis` del proyecto Compose `tienda-perf`.
2. Aplicar Flyway desde `V001` hasta la migración vigente.
3. Ejecutar `database/seeds/development/development_seed.sql`.
4. Ejecutar `performance-fixtures.sql` mediante `load-performance-fixtures.ps1`.
5. Iniciar el backend y la web de rendimiento.

El fixture vuelve a declarar `GENERAL` de forma idempotente. Cuando el seed de desarrollo ya creó esa lista, conserva su identificador y la referencia mediante el código estable `GENERAL`.

## Validación Estática

Desde la raíz del repositorio:

```powershell
.\tests\performance\fixtures\validate-performance-fixtures.ps1
```

La validación comprueba estructuralmente el contrato de cardinalidad, UUID estáticos, ausencia de secretos, sintaxis del cargador, operaciones prohibidas, allowlists y cobertura de las tablas necesarias. Es una validación estática: no sustituye la ejecución transaccional contra PostgreSQL ni las comprobaciones Smoke.

## Carga Mediante Docker Y `psql`

Con el ambiente `tienda-perf` ya migrado y después del seed de desarrollo:

```powershell
.\tests\performance\fixtures\load-performance-fixtures.ps1
```

`scripts/perf-environment-up.ps1` ya ejecuta este cargador automáticamente justo después de `db-seed` y antes de iniciar `backend` y `web-performance`. El comando anterior sirve para un replay manual dentro del mismo ambiente aislado, con la semántica no destructiva descrita abajo.

El cargador resuelve el contenedor de PostgreSQL por la etiqueta Compose y transmite el SQL por entrada estándar a:

```text
docker exec -i -e PGPASSWORD -e PERF_FIXTURE_USER_PASSWORD <database-container-id> psql -X -q -v ON_ERROR_STOP=1 ...
```

La contraseña nunca forma parte del SQL ni de los argumentos de `psql`. El cargador exige `PERF_FIXTURE_USER_PASSWORD` desde el `perf.env` aislado, debe coincidir con `K6_PERF_PASSWORD` y debe ser distinta de las credenciales administrativas o E2E. El valor nunca se imprime.

## Contrato De Datos

- una sucursal activa y dos almacenes;
- 150 presentaciones sin lote, una por VU potencial;
- 150 presentaciones con dos lotes FEFO cada una;
- stock físico consistente en ambos almacenes;
- lista `GENERAL`, con precio de sucursal para productos sin lote y precio global para productos con lote;
- 200 clientes y 20 proveedores sintéticos;
- 150 dispositivos `MOBILE_EMPLOYEE`, todos ligados al usuario restringido;
- 16 cajas registradoras;
- usuario `perf_operator` con permisos operativos limitados y acceso únicamente a la sucursal `PERF`.

Los UUID, códigos, cantidades y precios iniciales son reproducibles. En la primera carga se crean todos los balances. En un replay, `inventory.stock_balances` e `inventory.lot_balances` usan `ON CONFLICT ... DO NOTHING`: no restauran existencias, reservas, asignaciones, costo promedio ni versión, por lo que conservan los efectos de ventas y demás operaciones ya ejecutadas.

Los datos maestros sintéticos se normalizan mediante sus `UPSERT`. El estado de autorización es deliberadamente estricto: `perf_operator` debe tener una sola asignación `PERF_OPERATOR` en `PERF`, una sola sucursal activa y exactamente la allowlist documentada en `fixture-contract.json`. Si aparecen roles, sucursales o permisos adicionales, el loader no los elimina silenciosamente; las aserciones finales fallan y PostgreSQL revierte toda la transacción.
