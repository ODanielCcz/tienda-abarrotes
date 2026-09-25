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

El cierre elimina contenedores, red, volúmenes y credenciales efímeras. La evidencia
permanece bajo `%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts`, fuera de OneDrive;
sólo el informe sanitizado se versiona.

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

## Línea base técnica

El baseline se captura sin k6 activo, con 61 muestras cada cinco segundos durante
cinco minutos antes y después del calentamiento. Entre ambas ventanas ejecuta 20
peticiones seriales por cada endpoint crítico. La única ventana declarada como
oficial es `postWarm`; las muestras previas sirven para distinguir el arranque en
frío del estado estabilizado.

```powershell
# Preflight rápido: valida tienda-perf, loopback, salud, fixtures y PostgreSQL.
.\scripts\run-perf-baseline.ps1 -ValidateOnly

# Corrida oficial: suites backend/web, build Angular, 5 min pre-warm,
# calentamiento serial y 5 min post-warm.
.\scripts\run-perf-baseline.ps1
```

`-SkipProjectVerification` existe solo para diagnóstico y queda marcado en el
resumen como `DIAGNOSTIC`, `official=false` y `diagnosticBaseline`; nunca crea el
campo `officialBaseline` ni sustituye la corrida oficial. La ejecución exige un
árbol Git limpio.
Actuator/Prometheus se consulta con el usuario restringido `perf_operator`; el JWT
permanece únicamente en memoria y nunca se escribe ni imprime.

El preflight exige el estado inicial exacto del fixture: stock `10000` en la
presentación/almacén conocidos y cero ventas, pagos, operaciones Sync y carritos.
Si falla, no intentes reutilizar el volumen: ejecuta `perf-environment-down.ps1`
y `perf-environment-up.ps1` para recrear `tienda-perf`. También se validan el
proyecto y labels Compose, `POSTGRES_DB=tienda_performance`,
`POSTGRES_USER=tienda_owner`, `current_database()` y `current_user` antes de SQL.

La extensión `pg_stat_statements` se habilita de forma idempotente únicamente en
el PostgreSQL de rendimiento. Se capturan readiness, Prometheus, `docker stats`,
`pg_stat_database`, `pg_stat_activity`, locks y las 20 consultas con más tiempo
acumulado, sin almacenar texto SQL.
`pg_stat_statements` se reinicia justo antes de `postWarm`, de modo que sus datos
no incluyen el pre-warm ni las 120 solicitudes del calentamiento.

Las métricas Prometheus críticas deben existir y ser finitas en cada muestra;
las opcionales pueden faltar, pero nunca contener valores no finitos. Cada
muestra exige una fila única por servicio Docker y una instantánea PostgreSQL
válida. En cada muestra PostgreSQL, Redis y backend deben seguir `healthy`, nginx
debe continuar `running` y ningún servicio puede cambiar su contador de reinicios
respecto del preflight. Las muestras usan tiempos objetivo derivados del inicio de la ventana,
registran tiempo programado/real, jitter y duración total, y fallan si superan
`1500 ms` de jitter o `10 s` de sobretiempo. HTTP/login, Docker/psql y cada etapa
Gradle/npm tienen timeouts explícitos con terminación controlada.

Un lock exclusivo impide dos campañas simultáneas. Gradle/npm reciben un entorno
heredado mínimo y, antes de marcar una corrida `COMPLETED` o `DIAGNOSTIC`, todos
los JSON, JSONL, logs, Markdown y scrapes Prometheus se recorren para redactar
secretos exactos y JWT; una credencial residual hace fallar la captura.
Si esa protección falla en la ruta de error, la corrida falla cerrada: el
directorio autorizado se elimina o se mueve a la cuarentena externa. El error
combinado nunca incluye el secreto ni la causa interna del sanitizador.
Delete/quarantine solo aceptan la forma canónica
`artifacts\baseline\<yyyyMMddTHHmmssfffZ>` y exigen el marker de propiedad que
crea el runner. Antes de operar se inspeccionan `artifacts`, `baseline`, la
corrida y cada componente de cuarentena; cualquier symlink, junction o reparse
point cancela la operación. La ruta y el marker se comprueban nuevamente justo
antes de borrar o mover y el destino de cuarentena se valida también después.

Toda la evidencia cruda se conserva fuera de OneDrive, bajo
`%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts\baseline\<timestamp>`.
El resumen `baseline-summary.md` y su equivalente JSON contienen las medianas
`postWarm`, la revisión Git, SO, CPU, RAM, versiones Java/Node/npm/Docker/Compose
y el manifiesto del build.

## Carga backend reproducible

La carga usa un ciclo determinista de 20 operaciones con 55 % lecturas, 20 %
catálogo/contexto, 15 % ventas, 5 % pagos y 5 % Sync. Cada VU recibe su propia
presentación, cliente y dispositivo; las claves de idempotencia se derivan del
`RunId`, VU e iteración. El usuario sigue siendo `perf_operator`: no se amplía su
alcance global y todas las lecturas respetan la sucursal PERF. En `setup()`,
el JWT de ese usuario consulta las sucursales activas y valida que sólo sea
accesible la sucursal sintética PERF. Las métricas de trabajo incluyen
`module`, `endpoint`, `operation` y
`functional_result=success|unexpected` según el estado HTTP esperado; checks y
contadores adicionales verifican esquema y comportamiento de negocio.

```powershell
# Validador estructural y k6 inspect, sin tráfico.
.\tests\performance\k6\validate-load.ps1
node .\tests\performance\k6\test-auth-context.cjs
node .\tests\performance\k6\test-workload-metrics.cjs
.\tests\performance\scripts\run-perf-load.ps1 -ValidateOnly

# Diagnóstico de 10 segundos; nunca sustituye una corrida oficial.
$runId = [guid]::NewGuid().ToString()
.\scripts\run-perf-k6.ps1 -Script backend-load.js -RunId $runId -QuickLoad
.\tests\performance\scripts\validate-performance-invariants.ps1

# Campaña oficial: tres corridas independientes y 5 minutos de enfriamiento.
.\tests\performance\scripts\run-perf-load.ps1
```

Cada corrida oficial recrea la base efímera, ejecuta preflight, escala de 10 a
50 VU durante 12 minutos y exige cero `5xx`, cero errores funcionales, envelope
y correlation id válidos. Los umbrales son p95 menor a 500 ms para lecturas,
p95 menor a 900 ms para escrituras y p99 menor a 1500 ms para operaciones de
negocio. Tras k6 se comprueban stock no negativo, conservación de existencias,
un movimiento `SALE` por venta, ausencia de sobrepago y secuencias Sync sin
huecos.

Además del p95 agregado, cada lectura tiene un techo duro de 1500 ms y cada
escritura uno de 2000 ms. Un outlier que supere esos límites invalida checks;
los summaries incluyen p90, p95 y p99 para distinguir colas de latencia.

Los summaries comparables, invariantes y manifiesto de campaña se guardan fuera
del repositorio en `%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts\load`.
`-SkipCooldown` y valores menores de `RunCount` existen solo para diagnóstico y
no cumplen la aceptación oficial.

## Stress, spike y soak

Los tres perfiles reutilizan exactamente la mezcla e identidades deterministas
de carga. Cada perfil recrea `tienda-perf`, captura Actuator/Prometheus,
`docker stats`, PostgreSQL y readiness antes, durante y después del tráfico, y
termina ejecutando las invariantes transaccionales.

```powershell
# Solo valida contratos y ejecuta k6 inspect para los tres scripts.
.\tests\performance\scripts\run-fullstack-stress.ps1 -ValidateOnly

# Diagnóstico corto de los tres perfiles; no constituye evidencia oficial.
.\tests\performance\scripts\run-fullstack-stress.ps1 -Quick

# Campaña oficial: stress 16 min, spike 152 s más recuperación y soak 30 minutos.
.\tests\performance\scripts\run-fullstack-stress.ps1
```

Stress escala `50 → 75 → 100 → 150` VU y se aborta antes de subir más cuando
supera el límite de errores inesperados o pierde el contrato JSON. Spike sube
prácticamente de inmediato a 150 VU y registra cuántos segundos tarda readiness
en recuperarse desde el fin del tráfico (marker UTC emitido por k6), con un
máximo aceptable de 60 segundos. No hay una espera oculta a cero VU antes de
iniciar esa medición. Soak mantiene 25 VU y
compara memoria JVM, memoria del contenedor, threads, Hikari y sesiones de base
al principio y al final.

La evidencia cruda queda fuera de OneDrive en
`%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts\stress\<timestamp>`.
`campaign.json` distingue `PASSED`, `THRESHOLD_BREACHED` y `FAILED`; un punto de
quiebre esperado no oculta ni omite las comprobaciones de integridad.

La campaña oficial exige Git limpio y reconstruye los artefactos al comenzar.
Cada perfil registra revisión, ID y fecha de imagen y hashes SHA-256 del JAR
local y del contenedor; una diferencia detiene la ejecución. `Quick` permite
diagnóstico con cambios locales y deja esa limitación registrada.

Los escalones conservan métricas separadas y solo un escalón completado y con
umbrales válidos puede figurar como estable. El código nativo `99` identifica
un umbral incumplido; otros fallos de k6 son fallos de campaña. Un perfil
`FAILED` detiene la secuencia y conserva el ambiente para diagnóstico.

Pruebas del arnés sin tráfico:

```powershell
node tests/performance/k6/test-response-classification.cjs
node tests/performance/k6/test-profile-summary.cjs
.\tests\performance\scripts\test-stress-helpers.ps1
```

## Navegador De Producción

El procedimiento standalone, Lighthouse y combinado con 25 VU se encuentra en
[web/README.md](web/README.md). Los recorridos completos usan 50 ciclos por
viewport; las variantes `Quick` sólo validan el harness y no sustituyen esa evidencia.
