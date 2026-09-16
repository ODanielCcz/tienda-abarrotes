# Análisis De Estrés Y Seguridad Fullstack

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | TDA-QA-INF-001 |
| Versión | v0.2 |
| Estado | Rendimiento en revisión; seguridad pendiente |
| Responsable | ODCC / Codex |
| Fecha de actualización | 2026-09-16 |
| Clasificación | Interno |

## Control De Versiones

| Versión | Fecha | Autor | Cambio | Estado |
|---|---|---|---|---|
| v0.1 | 2026-09-05 | Codex | Resultados confirmados y pendientes explícitos | Borrador |
| v0.2 | 2026-09-16 | Codex | Angular standalone/combinado, Lighthouse y suites verificadas | En revisión |

## Resumen Ejecutivo De Rendimiento

Las tres ejecuciones Load concluyeron correctamente. En Stress, los escalones de
50 y 75 usuarios cumplieron los criterios; en 100 apareció un error 5xx al crear
una venta y se activó el corte preventivo. No se alcanzó el escalón de 150.
La comprobación posterior no encontró violaciones de las invariantes medidas.

El pico corregido de 150 usuarios pasó. Esto no contradice Stress: el pico dura
menos, parte de una base reiniciada y acumula menos datos. No garantiza soportar
150 usuarios sostenidos. Soak terminó correctamente. Angular mostró crecimiento
de heap por encima del límite tras 50 ciclos, un selector operativo oculto en
tablet/móvil y LCP superior a 4 s en dashboard y catálogo bajo Lighthouse móvil
simulado. Con 25 VU simultáneos, la navegación siguió por debajo de 200 ms p95;
el crecimiento de memoria excedió el umbral en escritorio y tablet. No se
registraron errores HTTP ni de consola durante los recorridos medidos.
Las pruebas de vulnerabilidades de esta campaña no han comenzado; requieren
revisión del informe de rendimiento completo.

## Alcance Y Condiciones

- Sólo `tienda-perf` y fixtures sintéticos; no base habitual.
- API 58081, web de producción Nginx 54201 y PostgreSQL 55434, en loopback.
- Intel i5-11400H, 6 núcleos/12 procesadores lógicos, 25.479.135.232 bytes de RAM;
  Windows 10.0.26200.0. Java 21.0.9, Node 24.18.0, npm 11.17.0,
  Docker Engine 29.7.2 y Compose 5.4.0.
- Hikari máximo 20, mínimo ocioso 5. Contenedores sin límites explícitos de CPU/RAM.
  Las cifras son de laboratorio compartido, no una estimación de producción.
- Harness Stress original: `f7d9f4d`; corrección k6: `1d34a97`.
- Harness de navegador versionado posteriormente en `9ae76ae`. Las corridas
  registran `1d34a97` con cambios locales del harness; los hashes del build
  servido permiten identificar el producto medido independientemente de ello.
- SHA-256 del JAR comparado entre host y contenedor:
  `298e2240b9951bf6335b1ebd7acc4c71df05623ea2508f056e0c4d061ff506ef`.

## Resultados Backend Confirmados

### Línea Base

Fuente: `baseline/20260901T044323568Z`, revisión `f2ea4e6`. Ventanas anterior y
posterior al calentamiento: 300 s programados y 61 muestras cada una; duración
real posterior 302,97 s. Medianas posteriores:

| Indicador | Mediana |
|---|---:|
| CPU backend, Docker | 0,27 % |
| Memoria del contenedor backend | 539,4 MiB |
| Memoria JVM usada, suma instrumentada | 305.320.896 bytes |
| Threads JVM | 32 |
| Hikari activas / ociosas / pendientes | 0 / 5 / 0 |
| Sesiones PostgreSQL | 6 |
| Locks en espera / deadlocks | 0 / 0 |

La memoria del contenedor no equivale al heap JVM. Las sesiones PostgreSQL en
espera incluyen esperas no relacionadas con locks; no representan por sí mismas
contención. Los scrapes de observabilidad también generan tráfico.

Verificación de esa línea base: Angular 58 archivos y 152 tests pasados. El comando
Gradle terminó correctamente, pero `:test` figuró `UP-TO-DATE`: esa salida no prueba
una reejecución fresca de todos los tests backend. Debe conservarse esta distinción
y realizar una ejecución sin caché de tareas antes del cierre global.

Verificación adicional del 16 de septiembre: `gradlew.bat test --rerun-tasks
--no-daemon` ejecutó las cinco tareas sin reutilizar su resultado; 86 suites,
341 tests, cero fallos, errores u omisiones. Angular volvió a aprobar 58 archivos
y 152 tests. `npm run build` pasó y sus 59 archivos coinciden por SHA-256 con el
build de la corrida combinada. Esto aporta evidencia fresca posterior a la
campaña, sin convertir retroactivamente el baseline cacheado en una prueba nueva.

### Smoke Y Adaptaciones De Perfiles

El Smoke implementado ejecuta una iteración integral con 1 VU y escrituras
deterministas, repetida tres veces para validar idempotencia. No es una prueba
sostenida de un minuto como la duración inicial propuesta. Spike ejecuta una
secuencia de 152 segundos más recuperación, no cuatro minutos de tráfico: su
objetivo es medir el salto súbito a 150 VU y la recuperación desde el fin real
del tráfico. Las duraciones reales y los comandos están versionados; estas
adaptaciones no deben confundirse con equivalencia exacta de los perfiles
iniciales ni con validación de carga sostenida adicional. Los 30 minutos de
Soak incluyen un minuto de ascenso, 28 minutos a 25 VU y un minuto de descenso.

### Load

| Corrida | Peticiones | p95 HTTP (ms) | p99 HTTP (ms) | Resultado |
|---|---:|---:|---:|---|
| 1 | 133.799 | 210,57 | 373,36 | PASSED |
| 2 | 167.203 | 98,13 | 187,78 | PASSED |
| 3 | 172.647 | 92,53 | 199,87 | PASSED |
| Mediana por métrica | 167.203 | 98,13 | 199,87 | — |

Fuente: `load/20260901T055132360Z-campaign.json`. Las medianas independientes
no representan una cuarta corrida real.

Medianas adicionales: 232,17 peticiones/s, p95 de lecturas 110,45 ms y p95 de
escrituras 58,44 ms. Las tres corridas tienen checks 100%, HTTP fallidos 0,
5xx inesperados 0 y errores de esquema 0 en sus summaries.
La validación Load comprobó stock negativo, sobrepagos, movimientos huérfanos y
secuencias/checkpoints Sync. La validación posterior de Stress amplió esa evidencia
con stock por lote y diferencias entre balances; no atribuir retrospectivamente
esas comprobaciones adicionales a los artefactos antiguos de Load.

### Stress

| Escalón | Peticiones del escalón | p95 (ms) | p99 (ms) | Estado |
|---|---:|---:|---:|---|
| 50 VU | 95.739 | 27,74 | 51,88 | Estable según criterios medidos |
| 75 VU | 107.435 | 228,89 | 378,04 | Estable según criterios medidos |
| 100 VU | 58.850 | 686,45 | 1.011,97 | Corte; escalón incompleto |
| 150 VU | 0 | — | — | No alcanzado |

Total: 262.026 peticiones. Un 5xx en `sales-order` dejó al consumidor sin `data`
y elevó `schema_error`, cuyo umbral es cero. No se alcanzó una tasa de 5xx del 1%:
el criterio de contrato fue más estricto. Recuperación posterior al tráfico: 3,312 s.
Hikari llegó al 100%; terminó con 0 activas, 20 ociosas y 0 pendientes. PostgreSQL
terminó con 0 esperas de locks y 0 deadlocks observados. Invariantes: PASS.

Limitación: k6 ignoró `gracefulRampDown` por estar en un nivel incorrecto de opciones.
Se conserva el resultado con esa advertencia; no valida exactamente el descenso
previsto. La corrección se probó con regresión RED/GREEN, `k6 inspect`, perfiles
cortos y repetición del pico.

### Spike Corregido

| Indicador | Resultado |
|---|---:|
| Objetivo | 150 VU |
| Peticiones | 74.562 |
| p95 / p99 | 327,78 / 455,97 ms |
| Readiness después del tráfico | 0,874 s |
| Integridad / perfil | PASS / PASSED |

Fuente: `stress/20260905T064647305Z/campaign.json`, run
`0291d68c-a78e-458c-b24c-0dae8dba3aaa`. Tráfico configurado: 2 min 32 s; después se
mide recuperación, sin una cola artificial a cero usuarios que oculte su tiempo.

### Soak

Campaña `stress/20260905T064647305Z`, run `c67e794c-8f9d-4749-9d1a-0d95e35711df`.
30 minutos con objetivo 25 VU y rampas de un minuto al inicio/final.

| Indicador | Resultado |
|---|---:|
| Peticiones / throughput | 345.359 / 191,86 por segundo |
| p95 / p99 HTTP | 121,43 / 280,60 ms |
| p95 lecturas / escrituras | 147,81 / 38,72 ms |
| Checks | 1.415.980 pasados, 0 fallidos |
| HTTP fallidos / 5xx / esquema | 0 / 0 / 0 |
| Recuperación tras tráfico | 1,628 s |
| Muestras de recursos | 222 |
| Hikari pico / activas finales / pendientes finales | 80% / 0 / 0 |
| Threads JVM iniciales / finales / pico | 33 / 47 / 49 |
| Memoria JVM usada inicial / final | 330.495.296 / 395.662.216 bytes |
| Variación de memoria JVM usada | +19,718% |
| Estado / integridad | PASSED / PASS |

El aumento de memoria no demuestra por sí solo una fuga: no se forzó GC backend y
se comparan puntos de una serie con asignaciones/recolecciones y calentamiento.
No equivale a memoria retenida después de GC. Tampoco se afirma ausencia absoluta
de fugas por una única prueba de 30 minutos.

Comparando las primeras y últimas ventanas de cinco minutos de muestras `during`,
la memoria usada promedio pasó de 310.858.873 a 327.069.008 bytes (aprox. +5,2%).
Los mínimos fueron 241.531.416 y 255.123.344 bytes, con 38 y 36 muestras.
La diferencia entre este resultado y el +19,7% entre dos puntos muestra por qué
no debe diagnosticarse una fuga usando sólo las mediciones inicial/final.

La consulta final verificó cero stock negativo general/por lote, cero diferencias
de stock, sobrepagos, movimientos huérfanos y desfases Sync. Registró 51.815 ventas,
17.276 pagos y 17.267 operaciones Sync/carritos. PostgreSQL terminó sin esperas de
locks ni deadlocks observados. El descenso interrumpió iteraciones del generador,
pero los summaries no registraron fallos HTTP ni de contrato por ello.

## Angular

Build observado: 97,56 kB iniciales y 23,53 kB de transferencia estimada. No incluye
la suma de todos los chunks lazy ni demuestra por sí mismo un buen LCP.
Corrida standalone `web/20260905T073240899Z-standalone`: 12 pruebas en 9,6 minutos;
7 aprobadas y 5 fallidas. Se completaron 50 ciclos de navegación y 50 ciclos de
diálogos en cada viewport, con recolección de basura antes de medir el heap.

| Viewport | Navegación p95 | Crecimiento heap navegación | Crecimiento heap diálogos |
|---|---:|---:|---:|
| Escritorio 1440×900 | 114,36 ms | 26,41% | 18,83% |
| Tablet 820×1180 | 98,79 ms | 23,24% | 19,00% |
| Móvil 390×844 | 95,78 ms | 24,06% | 19,41% |

Las tres pruebas de navegación excedieron el límite de crecimiento del 20%; las
de diálogos lo cumplieron. No se registraron errores de consola ni HTTP en estos
ciclos, ni escrituras de catálogo al abrir/cancelar diálogos. Las tres pruebas
de error API controlado mostraron el identificador de correlación esperado.
Las otras dos pruebas fallidas corresponden al selector oculto en tablet/móvil.

El aumento del heap es una alerta de retención, no una fuga demostrada: requiere
comparación de snapshots y rutas de retención. En escritorio, el ciclo final
regresó a 2 documentos, 1.256 nodos y 165 listeners, valores también observados
en el primer ciclo, mientras el heap siguió creciendo. No se atribuye todavía
el incremento a un componente concreto.

Las muestras finales de ciclo no mostraron desbordamiento horizontal del documento.
Durante los 50 ciclos medidos de navegación se registraron 0 tareas largas en
escritorio, 1 en tablet (59 ms acumulados) y 2 en móvil (107 ms acumulados).
Los ciclos de diálogos no añadieron tareas largas observadas. Estos conteos
excluyen calentamiento; no son una medición de INP ni prueban que cada pantalla
intermedia o tabla carezca de problemas de interacción o scroll.

Lighthouse `web/20260905T074835452Z-lighthouse` completó tres auditorías por ruta.
Medianas móviles simuladas, caché HTTP vacía y sesión conservada en la misma pestaña:

| Ruta | Rendimiento | Accesibilidad | Buenas prácticas | LCP | CLS |
|---|---:|---:|---:|---:|---:|
| Login | 98 | 100 | 100 | 2.244 ms | 0 |
| Dashboard | 80 | 100 | 100 | 4.011 ms | 0,000296 |
| Catálogo/productos | 75 | 100 | 100 | 4.154 ms | 0,000626 |

Login cumple todos los límites. Dashboard y catálogo incumplen rendimiento ≥85
y LCP ≤2.500 ms. Las puntuaciones automáticas de accesibilidad no sustituyen una
revisión manual ni contradicen el selector inaccesible observado por Playwright.
Se verificó la ruta final de cada auditoría para excluir redirects a login.

El ejecutor usa Lighthouse 13.4.1 sobre Puppeteer y Chromium Headless Shell
151.0.7922.34. Es una adaptación de Lighthouse CI a la autenticación sessionStorage.
El Chrome completo local falló antes de auditar (`spawn UNKNOWN`); el binario
headless fue validado por separado. Una validación previa tuvo muestras incompletas
por no esperar al formulario Angular; se corrigió la espera y no se mezclaron sus
resultados con esta serie completa. No son mediciones de dispositivos físicos.

### Navegador Con Carga Backend

Corrida `web/20260905T075857561Z-combined`, terminada el 5 de septiembre y
verificada nuevamente el 16 tras retomar la sesión. Completó 50 ciclos de
navegación y 50 de diálogos por viewport. Playwright: 8 pruebas aprobadas y 4
fallidas en 10,8 minutos. Los fallos fueron memoria en escritorio/tablet y el
selector oculto en tablet/móvil.

| Viewport | Navegación p95 | Heap navegación | Heap diálogos | Tareas largas navegación |
|---|---:|---:|---:|---:|
| Escritorio | 143,09 ms | +22,07% | +13,93% | 10 / 568 ms acumulados |
| Tablet | 159,36 ms | +25,15% | +15,21% | 6 / 398 ms acumulados |
| Móvil | 198,58 ms | +19,33% | +18,18% | 23 / 1.373 ms acumulados |

Las 130 comprobaciones de control, espaciadas aproximadamente cinco segundos,
confirmaron 25 VU activos. No es monitorización continua subsegundo. k6 procesó
140.943 solicitudes a 215,41 solicitudes/s; p95 HTTP 55,48 ms, p99 HTTP 112,17 ms,
p95 de lecturas 56,21 ms y p95 de escrituras 50,43 ms. Hubo 577.869 checks aprobados,
cero fallos HTTP, cero 5xx inesperados y cero errores de esquema o funcionales.
El generador terminó por la parada REST solicitada al finalizar el navegador,
no por agotamiento de la duración máxima de una hora.

Los tres recorridos conservaron cero errores de consola y HTTP; los diálogos
no emitieron escrituras de catálogo. La inyección controlada de error conservó
el correlation id visible bajo carga en los tres tamaños. Las invariantes
posteriores pasaron: 21.153 ventas, 7.054 pagos y 7.046 operaciones Sync/carritos,
sin stock negativo, diferencias de stock, sobrepagos ni movimientos huérfanos.

El resultado de heap móvil cumple en combinado pero no en standalone. Esta
variabilidad no prueba que la carga reduzca retención; no se selecciona la mejor
corrida ni se declara resuelta la alerta. El p95 de navegación aumenta con carga
en los tres tamaños, manteniéndose dentro de 1 s.

## Observaciones Y Certeza

| ID | Clasificación | Evidencia / pendiente |
|---|---|---|
| PERF-001 | Confirmada | Saturación del pool y aumento de latencia a 100 VU |
| PERF-002 | Confirmada, causa desconocida | Un 5xx al crear venta; log causal no preservado antes del reinicio automático |
| PERF-003 | Hipótesis | Pool/consultas pueden contribuir; no atribuirles el 5xx sin evidencia causal |
| PERF-004 | Corregida en harness | Opción ignorada por k6, con regresión y ejecución corregida |
| PERF-005 | Limitación | Variación de corridas y equipo compartido; no extrapolar capacidad productiva |
| WEB-001 | Confirmada en prueba corta y código | `.context-selector` usa `display:none` a ≤1050 px; no permite seleccionar sucursal/almacén desde tablet/móvil |
| WEB-002 | Umbral excedido; causa pendiente | Heap tras 50 ciclos de navegación crece 23,24–26,41% en los tres viewports; límite 20%, sin prueba causal de fuga |
| WEB-003 | Confirmada en laboratorio | Dashboard y catálogo: rendimiento Lighthouse 80/75 y LCP 4,01/4,15 s; incumplen 85 y 2,5 s |

`WEB-001` se localiza en
`apps/web/src/app/layout/app-shell/operational-context-selector/operational-context-selector.scss`.
No se modifica código de interfaz durante esta campaña. Una prueba independiente
deja constancia de su indisponibilidad. Para medir la navegación restante se
prepara el contexto en escritorio y se restaura el viewport objetivo antes de
calentar y medir: son resultados con contexto previamente configurado, no una
validación del flujo inicial completo en móvil.

La primera prueba corta también detectó dos problemas del harness: lectura de
claves con dígitos (`E2E_USERNAME`) y clic sobre el elemento interior cubierto por
el área táctil del paginador Material. No se atribuyen al backend; el lector se
corrigió con prueba de regresión y el selector se opera mediante teclado.

No aumentar el pool automáticamente: podría trasladar la saturación a PostgreSQL.
Primero correlacionar consultas, esperas y errores.

## Interpretación Y Próximas Investigaciones

| Superficie | Evidencia | Interpretación y siguiente paso |
|---|---|---|
| Navegador, navegación SPA | p95 standalone <115 ms; heap >20% tras 50 ciclos | Latencia interna cumple, retención no. Comparar snapshots de heap y objetos retenidos antes de cambiar componentes |
| Navegador, carga inicial | Lighthouse dashboard/catálogo LCP >4 s | Problema confirmado bajo simulación móvil. Perfilar carga, ejecución y elemento LCP; no atribuirlo sólo al tamaño inicial del bundle |
| Interfaz responsive | Selector oculto a ≤1050 px | Revisar cómo ofrecer contexto operativo en pantallas estrechas; el ajuste temporal del harness no resuelve el flujo del usuario |
| Aplicación / JVM / pool | Stress 100 VU, Hikari 20/20 y latencia creciente | Saturación observada, no causalidad exclusiva. Correlacionar peticiones, tiempos de conexión y consultas |
| Error de venta | Un 5xx y fallo de contrato en Stress | Reproducir con preservación del log causal y correlation id antes de proponer una corrección |
| PostgreSQL | Integridad posterior aprobada y sin deadlocks observados | No demuestra ausencia de consultas lentas ni de esperas breves entre muestras |
| Redis | Campaña normal con tokens reutilizados | No se midió aquí rate limiting ni fallo de Redis; pertenecen a la fase de seguridad autorizada posterior |

No hay evidencia suficiente para recomendar aumentar automáticamente CPU, RAM,
concurrencia o conexiones. El último escalón estable observado fue 75 VU para
esta mezcla y duración; no es una garantía de capacidad sostenida en producción.

## Reproducción Del Rendimiento

Desde la raíz del repositorio, con Docker disponible, el build de producción y
las dependencias preparadas y Git limpio para baseline/Stress oficiales. Los runners recrean sólo la base sintética de
`tienda-perf`; no ejecutarlos simultáneamente.

```powershell
.\scripts\perf-environment-up.ps1
.\scripts\run-perf-baseline.ps1
.\tests\performance\scripts\run-perf-load.ps1
.\tests\performance\scripts\run-fullstack-stress.ps1
.\tests\performance\scripts\run-web-performance.ps1 -Mode standalone
$env:PERF_LIGHTHOUSE_CHROME_PATH = Join-Path $env:LOCALAPPDATA 'ms-playwright\chromium_headless_shell-1234\chrome-headless-shell-win64\chrome-headless-shell.exe'
.\tests\performance\scripts\run-web-performance.ps1 -Mode lighthouse
.\tests\performance\scripts\run-web-performance.ps1 -Mode combined
# Al cerrar el entorno desechable:
.\scripts\perf-environment-down.ps1
```

La ruta de Chromium corresponde a la versión instalada en esta campaña y debe
ajustarse si cambia Playwright. El procedimiento Load y las opciones de captura
están en `tests/performance/README.md`; el detalle de emulación y autenticación
Lighthouse está en `tests/performance/web/README.md`.

## Evidencia Y Pendientes

Artefactos bajo `%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts`.
El 16 de septiembre se cerró `tienda-perf`: contenedores y volúmenes del proyecto
ausentes y `perf.env` eliminado. Se conserva la evidencia local para trazabilidad;
no se publicó ni se incluyó en Git.
`stress/20260905T061951941Z` se interrumpió antes de Soak: su manifiesto conserva
`RUNNING`, pero el proceso terminó. No confundirlo con ejecución activa.

- Si se requiere descartar fugas de largo plazo, ampliar duración y analizar memoria retenida/GC.
- Revisar este primer análisis y sus adaptaciones metodológicas con el usuario.
- Después: escaneo estático, dependencias y pruebas dinámicas autorizadas.
- Correcciones de vulnerabilidades: acordarlas tras el informe de seguridad.
