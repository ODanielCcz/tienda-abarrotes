# Campaña De Rendimiento

Este directorio contiene herramientas exclusivas para el proyecto Docker efímero `tienda-perf`. No ejecutes estos escenarios contra producción ni contra una base con información real.

## Preparación

```powershell
.\scripts\perf-environment-up.ps1
```

El script genera secretos aleatorios bajo
`%LOCALAPPDATA%\TiendaAbarrotes\performance\perf.env`, fuera del repositorio y de OneDrive. Luego construye backend y Angular, migra una base nueva y expone únicamente en `127.0.0.1`:

- backend: `http://localhost:58081`;
- Angular: `http://localhost:54201`.

## Cierre

```powershell
.\scripts\perf-environment-down.ps1
```

El cierre elimina contenedores, red, volúmenes y credenciales efímeras. Los artefactos sanitizados permanecen bajo `artifacts/quality` para elaborar el informe.

Los resultados crudos se guardan en
`%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts`. Solo se copiarán al repositorio métricas y evidencias previamente sanitizadas. El contenedor k6 recibe exclusivamente sus credenciales de prueba; no recibe los secretos de PostgreSQL, Redis ni de firma JWT.

Para reutilizar imágenes ya construidas, sin reutilizar la base:

```powershell
.\scripts\perf-environment-up.ps1 -SkipBuild
```

La opción `-ReuseExisting` debe usarse únicamente cuando se quiera conservar deliberadamente el volumen efímero entre pasos de una misma campaña. La ejecución predeterminada comienza desde cero.

## Smoke k6 determinista

Ejecuta estos comandos únicamente después de preparar `tienda-perf`; el contenedor `k6` recibe las credenciales efímeras mediante Compose y no las imprime.

```powershell
# Valida el nombre Compose efectivo, la estructura y la sintaxis sin solicitudes HTTP.
.\scripts\run-perf-k6.ps1 -ValidateComposeProject
.\tests\performance\k6\validate-smoke.ps1
.\scripts\run-perf-k6.ps1 -Command inspect

# Smoke normal: 1 VU y 1 iteración.
.\scripts\run-perf-k6.ps1

# Prueba negativa deliberada: debe terminar con checks fallidos por URL inválida.
.\scripts\run-perf-k6.ps1 -ExpectInvalidUrl

# Prueba de protección contra aborto de iteración: debe terminar con `smoke_completion` fallida.
.\scripts\run-perf-k6.ps1 -ExpectAbortMidway
```

No invoques `docker compose` directamente para k6: el wrapper siempre importa el `perf.env` efímero, usa ambos archivos Compose, fuerza `tienda-perf` y ejecuta `run --rm --no-deps`.

La readiness del actuator no usa `ApiResponseDto`: el smoke valida explícitamente el JSON `{ "status": "UP" }` y el encabezado `X-Correlation-ID`. Todas las rutas de negocio validan el envelope completo.

`SMOKE_RUN_ID` es un UUID opcional. Mantén el mismo valor para repetir exactamente las mismas operaciones; el predeterminado es estable y permite tres repeticiones sin crear ventas, pagos o Sync duplicados. Los IDs de presentación, dispositivo y precio se derivan de `fixture-contract.json` y de las fórmulas documentadas en `performance-fixtures.sql`. El smoke usa exclusivamente el usuario restringido `perf_operator` configurado en el `perf.env` efímero.

`PERF_PASSWORD` y `PERF_FIXTURE_USER_PASSWORD` deben ser iguales entre sí, pero distintos de las contraseñas bootstrap/E2E. El cierre del ambiente admite explícitamente envs legados: solo usa placeholders aleatorios en memoria para interpolar Compose y nunca habilita k6.

El inicio es fail-closed: las tres claves `PERF_*` deben estar presentes dentro de `perf.env`; variables heredadas del proceso no son válidas. Si un `perf.env` legado no cumple el contrato, ejecuta primero `./scripts/perf-environment-down.ps1` y después `./scripts/perf-environment-up.ps1`; `up` no elimina volúmenes ni credenciales automáticamente.

Un `SMOKE_RUN_ID` nuevo no aísla por sí solo un ambiente ya usado: recrea `tienda-perf` o selecciona un dispositivo garantizado como no usado antes de investigar. La evidencia oficial siempre debe usar un ambiente recreado y el mismo ID en sus tres repeticiones.
