# Análisis De Estrés Y Seguridad Fullstack

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | TDA-QA-INF-001 |
| Versión | v0.5 |
| Estado | Rendimiento revisado; SEC-001 y SEC-002 remediados y verificados; SEC-003–005 pendientes de decisión |
| Responsable | ODCC / Codex |
| Fecha de actualización | 2026-09-24 |
| Clasificación | Interno |

## Control De Versiones

| Versión | Fecha | Autor | Cambio | Estado |
|---|---|---|---|---|
| v0.1 | 2026-09-05 | Codex | Resultados confirmados y pendientes explícitos | Borrador |
| v0.2 | 2026-09-16 | Codex | Angular standalone/combinado, Lighthouse y suites verificadas | En revisión |
| v0.3 | 2026-09-24 | Codex | Escaneo estático/SCA, ZAP pasivo, pruebas dirigidas y cinco hallazgos validados | Para priorización |
| v0.4 | 2026-09-24 | Codex | ZAP autenticado, prueba fiscal sintética y remediación priorizada de SEC-001/002 | En verificación |
| v0.5 | 2026-09-24 | Codex | Verificación del diff sin nuevos hallazgos, suite completa y cierre del laboratorio aislado | En revisión |

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
El usuario confirmó la lectura del primer informe y autorizó continuar con
seguridad. La campaña produjo cinco hallazgos reproducibles: dos de
severidad alta, dos media y uno baja. Tras la priorización aprobada, se corrigieron
SEC-001 y SEC-002; los otros tres hallazgos siguen documentados sin cambios
productivos en esta fase.

## Resumen Ejecutivo De Seguridad

El riesgo principal está en la integridad transaccional. Dos confirmaciones
simultáneas del mismo conteo físico duplicaron movimiento y ajuste de stock;
un pago concurrente con el cierre de caja quedó registrado en una sesión
cerrada sin integrarse al monto esperado. Dos cambios simultáneos crearon un
ciclo en categorías. Además, una venta de la sucursal B copió y expuso el costo
de la sucursal PERF, y un usuario Sync no vinculado consumió la cuota del
dispositivo ajeno. Los cinco hallazgos tienen confianza alta por reproducción
local y trazabilidad al código. Las pruebas de carga previas no detectaron
estas carreras concretas y no deben interpretarse como garantía de ausencia de
fallas de concurrencia. SEC-001 y SEC-002 se reprodujeron antes del parche y
pasaron después con pruebas deterministas; un escaneo formal del diff no encontró
vulnerabilidades nuevas en los tres archivos productivos modificados.

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

## Campaña De Seguridad: Alcance Y Modelo De Amenazas

Objetivo autorizado: sólo el proyecto Docker tienda-perf, API 127.0.0.1:58081,
Angular 127.0.0.1:54201, PostgreSQL efímero y Redis interno. Código auditado:
revisión da1d30618609371014ef0f849d468e4833e68353 (1.163 archivos
versionados), con Angular, backend, SQL, Compose/Docker, CI, dependencias,
logs y configuración. Las pruebas posteriores no tocaron producción ni datos
personales reales. Los fixtures nuevos y vínculos temporales se descartaron
o restauraron; el proyecto entero se destruye al cerrar el laboratorio.

Activos: credenciales/JWT, datos fiscales y de clientes, inventario por
sucursal y lote, cobros/caja, jerarquía de catálogo y operaciones de
dispositivos Sync. Fronteras: anónimo→API, usuario→permisos, usuario→sucursal,
dispositivo→titular, aplicación→PostgreSQL/Redis, build→imagen desplegada.
Se consideraron las clases de acceso roto a objetos/funciones, agotamiento
de recursos y configuración de la [OWASP API Security Top 10 2023](https://api-security.owasp.org/editions/2023/en/0x00-header/),
y los controles de autenticación, autorización, validación y lógica de negocio
de [OWASP ASVS 5.0](https://owasp.org/projects/asvs/).

### Cobertura Y Herramientas

| Control | Evidencia | Límite de interpretación |
|---|---|---|
| Codex Security estándar | Escaneo completo `d22e24a1-dea5-4cd4-9db1-f9e2f4b87399`: 16 superficies; cinco hallazgos validados contra fuente y laboratorio | Sin subagentes por instrucción vigente; una revisión principal sobre la revisión Git indicada |
| Gitleaks, historial completo | 133 commits; dos coincidencias en idempotencyKey fijo de test histórico | Ambos falsos positivos, no credenciales |
| npm audit | 0 vulnerabilidades runtime y 0 incluyendo desarrollo | Base de avisos distinta de Trivy |
| Grafo Gradle runtime | Resuelto sin red; Spring Boot 4.1.1, Security 7.1.1, Tomcat 11.0.24, Jackson 3.1.5/2.21.5 y PostgreSQL 42.7.12 | Las CVE de paquetes se contrastaron con el escaneo de imagen; resolver el grafo no prueba ausencia de vulnerabilidades |
| Trivy filesystem/configuración | 1 CVE media Angular y 2 avisos bajos HEALTHCHECK | Sin ruta explotable de Angular en el despliegue actual; Compose define healthchecks |
| Trivy imagen backend | 39 coincidencias: 3 críticas, 9 altas, 9 medias, 18 bajas | Clasificación de paquete, no 39 explotaciones confirmadas |
| Bundles Angular | 59 artefactos; sin source maps ni patrones de claves/URLs privadas buscados | Búsqueda de patrones, no prueba universal de ausencia |
| ZAP web pasivo | 7 URL en baseline CLI; 8 tipos de aviso, principalmente cabeceras del Nginx de laboratorio | Sin escaneo activo ni navegación autenticada completa |
| ZAP OpenAPI pasivo | 157 URL importadas; 1 aviso de cabecera CORP en /v3/api-docs | Modo seguro; no se enviaron payloads activos a las 157 rutas |
| ZAP autenticado | Cuatro GET del operador restringido: plan automatizado completado con salida 0, respuestas esperadas 200 y cero alertas en el reporte JSON | No cubre escrituras ni navegación autenticada completa |
| HTTP dirigido | Autorización, JWT, SQL/XSS de búsqueda, CORS, límites, Redis, concurrencia | Sólo los vectores y fixtures descritos |
| Integración transaccional inicial | 9 tests reejecutados antes de la remediación, 0 fallos/errores/omitidos | No incluían las carreras SEC-001/002 |
| Integración transaccional corregida | 12 tests de la clase de concurrencia, incluidos los tres nuevos casos de conteo, pago y devolución frente a cierre | PostgreSQL real mediante Testcontainers |

Tras las correcciones se repitió `scripts/run-security-assessment.ps1` contra
la imagen reconstruida. Pasaron el plan ZAP pasivo, la importación segura de
157 rutas OpenAPI y los casos HTTP de autorización, inyección y límites. Los
JSON locales están en `security-20260925T034908Z`: el baseline web tuvo cinco
tipos de aviso y el OpenAPI cuatro (uno bajo por CORP y tres informativos,
incluidos errores 4xx esperables al visitar rutas protegidas sin sesión).
Este segundo barrido pasivo no prueba por sí mismo las carreras; las regresiones
con PostgreSQL y barreras temporales son la evidencia de esas invariantes.

La importación OpenAPI del plan Automation Framework falló por una etiqueta
duplicada en la especificación; el escáner API en modo seguro importó 157 URL y
terminó. El plan base se dejó en spider web + GET de /v3/api-docs, y el runner
ejecuta aparte el escáner API. Tras autorización expresa del usuario, el plan
autenticado se ejecutó con un JWT del operador sintético entregado sólo al
contenedor ZAP aislado. El job `requestor` completó los cuatro GET esperados;
el reporte `security-auth-20260925T033152Z/zap-authenticated.json` registró
un sitio y cero alertas. El JWT no se guardó en el repositorio ni en el
informe. No se ejecutó un escaneo activo ni se enviaron escrituras.

Las cabeceras anti-clickjacking, nosniff, CSP, Permissions Policy y COEP/CORP
faltantes que ZAP observó en la web corresponden al Nginx de rendimiento
local; no se atribuyen automáticamente a un despliegue productivo no evaluado.
El backend respondió con nosniff, anti-framing, no-store y correlation ID.

### Dependencias Y Falsos Positivos

Trivy marcó CVE-2026-88059 en @angular/common 22.1.0 (corrección 22.1.1).
El [aviso de Angular](https://github.com/angular/angular/security/advisories/GHSA-p297-fm68-3q8c)
requiere SSR/hydration, un cliente HTTP hijo con delegación al padre,
interceptor de autenticación padre y caché HTML compartida. Esta app construye
sólo navegador y no usa esas condiciones; se recomienda actualizar como
mantenimiento, no presentarlo como explotación confirmada. npm audit devolvió
cero vulnerabilidades con y sin dependencias de desarrollo.

La imagen contiene Tomcat 11.0.24 con tres CVE que Trivy etiqueta críticas,
además de OpenSSL, Expat y SQLite con avisos altos. La
[evaluación oficial de Tomcat 11](https://tomcat.apache.org/security-11)
califica esos casos en el contexto de security-constraints de contenedor,
DIGEST o FORM; la app usa JWT stateless de Spring y no esas modalidades.
Las CVE de SQLite requieren base/SQL malicioso según
[SQLite](https://www.sqlite.org/cves.html), mientras la app usa PostgreSQL.
La explotabilidad de los avisos restantes de la imagen no se demostró; se
recomienda reconstruir con base actualizada y reescanear. Los dos avisos
HEALTHCHECK son de Dockerfile, pero el Compose evaluado sí configura checks
para backend y base. Gitleaks detectó dos valores de idempotencia sintéticos
en un test histórico: no eran API keys, pese a la regla genérica aplicada.
Las acciones CI están fijadas por SHA y el Dockerfile backend usa usuario
no privilegiado.

### Controles Dinámicos Que Funcionaron

Anónimo 401; operador autorizado 200; lectura de usuarios sin permiso 403;
JWT alterado, expirado o invalidado por cambio temporal de auth_version 401;
nuevo token 200 y versión restaurada. Un operador de PERF obtuvo 403 para
una segunda sucursal sintética, que tampoco apareció en su listado; el
administrador sí obtuvo 200. La búsqueda SQL "' OR 1=1--" devolvió cero
resultados, igual que una búsqueda sin coincidencias; la cadena XSS
reflejada no apareció en respuesta. Un origen CORS no autorizado dio 403
sin Access-Control-Allow-Origin; cuerpo de 1,1 MB dio 413 y payload Sync de
profundidad 21 dio 400.

Un objeto JSON con operador `$gt` enviado como cantidad numérica de venta
recibió 400 `MALFORMED_JSON` (`sec-json-injection`). La asignación masiva de
`branchId`, `status`, `paymentStatus`, `total`, `createdBy` y `unitCost`
en una venta del operador (`sec-mass-assignment`) no sobreescribió los campos
del servidor: respuesta 201 y fila SQL con sucursal PERF, estado CONFIRMED,
pago PENDING y total 26,68. Se almacenó en una categoría sintética un
marcador XSS HTML (`sec-stored-xss`); la API lo devolvió como JSON y Chromium
lo mostró como texto escapado en la lista de categorías, sin crear imagen ni
ejecutar JavaScript. Esta comprobación cubre esa vista, no todas las
presentaciones posibles del valor.

En el login distribuido, cinco contraseñas erróneas por par IP/cuenta dieron
401 y la sexta 429. Diez fallos del mismo usuario ficticio repartidos entre
dos IP dieron 401; el intento desde una tercera IP dio 429 por cuenta.
Con nombres distintos desde una IP, el intento posterior al límite 20 dio
429. Al detener sólo Redis de tienda-perf: readiness 503, liveness 200, login
503 y un JWT previo siguió sirviendo GET 200. Redis se reinició y readiness
volvió a 200. La inspección de logs del backend no encontró patrones JWT,
cabeceras Bearer ni coincidencias exactas de contraseñas/secretos sintéticos.
Además, se creó en la sucursal PERF un perfil emisor fiscal enteramente
sintético (HTTP 201) con marcador único. En la ventana de esa operación, ni
el marcador, ni el RFC sintético, ni el payload JSON completo, ni el JWT
aparecieron en los logs de backend o PostgreSQL. Es una comprobación de esa
ruta y ventana, no una prueba universal de ausencia de filtración. La
configuración del laboratorio desactiva `pgaudit.log_parameter`.

### Hallazgos Confirmados Y Prioridad

| ID | Severidad / confianza | CWE / clase | Resultado | Esfuerzo preliminar | Prioridad |
|---|---|---|---|---|---:|
| SEC-001 | Alta / alta | CWE-362, integridad de operación | Conteo confirmado dos veces: stock +2 y dos movimientos | Medio | 1 |
| SEC-002 | Alta / alta | CWE-367, estado de negocio | Pago entra en caja cerrada sin reflejarse en esperado | Medio | 2 |
| SEC-003 | Media / alta | CWE-367, integridad de catálogo | Dos actualizaciones crean ciclo de categorías | Alto | 3 |
| SEC-004 | Media / alta | CWE-862, aislamiento entre sucursales | Venta B usa y expone costo de PERF | Bajo–medio | 4 |
| SEC-005 | Baja / alta | CWE-863, agotamiento de recursos | Usuario ajeno consume cuota Sync del titular | Bajo | 5 |

La estimación es relativa y debe confirmarse al diseñar cada corrección; el
orden prioriza impacto e integridad antes que menor esfuerzo. SEC-004 también
se relaciona con la exposición de propiedades de objeto descrita por
[OWASP API3:2023](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/).

Reproducción mínima (siempre con JWT del rol indicado y objetos sintéticos):

| ID | Solicitud de prueba | Respuesta y contraste |
|---|---|---|
| SEC-001 | Dos `POST /api/v1/inventory/counts/{id}/confirm` simultáneos, sin cuerpo, sobre un DRAFT con diferencia +1 | 200/200; dos movimientos y stock +2 |
| SEC-002 | Sobre una venta CONFIRMED y caja OPEN: `POST /api/v1/cash/sessions/{id}/close` con `countedCashAmount:0` y `POST /api/v1/sales/orders/{id}/payments` con `paymentMethod:CASH`, `cashSessionId`, `amount:26.68`, `idempotencyKey` | 200/201; caja CLOSED con esperado 0 y pago de 26,68 |
| SEC-003 | Dos `PUT /api/v1/catalog/categories/{id}` simultáneos con `code`, `name` y `parentCategoryId` cruzados A→B y B→A | 200/200; ambas referencias mutuas en SQL |
| SEC-004 | Como usuario sólo de B, `POST /api/v1/sales/orders` con `warehouseId` de B, `idempotencyKey` nuevo e ítem de presentación compartida, cantidad 1 y precio vigente | 201; `unitCost=10` en vez del costo 99 de B; GET de PERF 403 |
| SEC-005 | Como usuario no vinculado, 300 `POST /api/v1/sync/inbox` con `deviceId` ajeno, UUID nuevos, `deviceSequence`, `operationType:CART_UPSERT`, `aggregateType:CART`, `payload` y `clientCreatedAt` válidos | 409 en los intentos ajenos; luego 429 al titular |

SEC-001 — Dos confirmaciones de un conteo. Precondición: permiso
INVENTORY_COUNT_CONFIRM sobre un almacén autorizado y un conteo DRAFT con
diferencia +1. Dos POST concurrentes a /api/v1/inventory/counts/{id}/confirm
devolvieron 200 (correlation IDs `sec001-confirm-1` y
`sec001-confirm-2`, conteo `58397cbe-668d-4b05-b23b-8a8e8830d61c`);
stock 10.000→10.002 y dos movimientos INVENTORY_COUNT,
cuando correspondían 10.001 y uno. La lectura DRAFT en
apps/backend/src/main/java/com/odcc/tienda/modules/inventory/adapter/out/persistence/jdbc/JdbcAdvancedInventoryRepositoryAdapter.java:120
no bloquea el conteo; el stock se ajusta antes de UPDATE, que sólo filtra por
ID en la línea 149. Se restableció stock 10.000 y se retiraron los registros
del conteo sintético. Recomendación: reclamar/bloquear el DRAFT antes del
ajuste, transición condicional de una sola fila y unicidad del movimiento por
conteo. Regresión: dos confirmaciones paralelas deben producir un único
movimiento y un único incremento.

SEC-002 — Pago frente a cierre de caja. Precondición: permisos de pago y
cierre, venta confirmada y sesión OPEN de la misma sucursal. Se ordenó la
carrera con un bloqueo SQL temporal del registro sintético. El cierre devolvió
200 (`sec002-close`) y el pago CASH 201 (`sec002-payment`); después,
la sesión `3305593b-ce9e-4999-9372-2617ea56fb9a` estaba CLOSED con
expectedAmount 0 y diferencia 0, mientras la venta
`cf81035b-f54c-4e10-b60f-9e2faea3d826` estaba PAID y había movimiento
SALE IN por 26,68. La primera reproducción mostró el mismo desajuste por
25,52. La lectura OPEN sin bloqueo en
apps/backend/src/main/java/com/odcc/tienda/modules/sales/adapter/out/persistence/jdbc/JdbcSalesPaymentRepositoryAdapter.java:173
precede a la inserción del movimiento en la línea 92. El cierre calcula el
esperado y cambia estado bajo otro bloqueo en
apps/backend/src/main/java/com/odcc/tienda/modules/cash/adapter/out/persistence/jdbc/JdbcCashSessionRepositoryAdapter.java:82.
Recomendación: serializar pago y cierre sobre la misma fila de sesión,
revalidando OPEN antes de persistir el movimiento. Regresión: pago incluido
en el esperado o rechazado, nunca posterior al cierre.

SEC-003 — Ciclo de categorías. Precondición: CATALOG_CATEGORY_UPDATE y dos
categorías raíz sintéticas. Enviar a la vez PUT A.parent=B y PUT B.parent=A
dio 200/200 (correlation IDs `sec003-a` y `sec003-b`); PostgreSQL conservó
las dos referencias mutuas. El mismo orden
secuencial dio 200/400. La consulta de ancestros en
apps/backend/src/main/java/com/odcc/tienda/modules/catalog/application/usecase/UpdateCategoryService.java:46
y el guardado en la línea 59 no comparten serialización del subárbol.
Ambas referencias se devolvieron a NULL. Recomendación: bloqueo/serialización
de cambios jerárquicos y comprobación del ciclo dentro de esa sección
transaccional. Regresión: sólo uno de los dos cambios paralelos se acepta.

SEC-004 — Costo entre sucursales. Precondición: presentación compartida con
balance en dos almacenes, costos distintos y usuario con SALES_ORDER_CREATE
sólo en B. La sucursal PERF tenía costo 10 y B costo 99. El usuario B obtuvo
403 al leer PERF, pero creó ventas en B cuyo ítem mostró unitCost 10;
la fila de venta persistió 10 mientras el balance del almacén B seguía en 99.
La última venta devolvió 201 con correlation ID `sec004-cost-b`.
La unión de stock por presentación omite warehouse_id y usa LIMIT 1 en
apps/backend/src/main/java/com/odcc/tienda/modules/sales/adapter/out/persistence/jdbc/JdbcSalesOrderRepositoryAdapter.java:444.
Impacto: margen/costo contable incorrecto y exposición de costo de otra
sucursal en la respuesta. Recomendación: unir por warehouse_id de la venta,
con pruebas de costos distintos por sucursal y de lectura acotada.

SEC-005 — Cuota Sync ajena. Precondición: SYNC_INBOX_WRITE y UUID de un
dispositivo no vinculado. El titular obtuvo checkpoint 200 y el emisor ajeno
409. Trescientos POST ajenos al inbox dieron 409, pero el siguiente POST del
titular recibió 429 (correlation ID
a87f5c4a-1bba-4515-92a1-091f312a0549). En
apps/backend/src/main/java/com/odcc/tienda/modules/sync/application/usecase/SyncService.java:60
se consume la cuota por deviceId antes de la comprobación de vínculo de la
línea 61. El vínculo temporal sintético fue restaurado. Impacto: bloqueo
temporal de Sync sin acceso a los datos del dispositivo. Recomendación:
autorizar primero y limitar intentos inválidos por actor/IP por separado.

Las segundas reproducciones capturaron correlation IDs para SEC-001 a
SEC-004; SEC-005 ya los tenía. Los UUID y contrastes SQL anteriores identifican
los objetos sintéticos. No se informan como
vulnerabilidades adicionales el error 5xx de Stress (causa desconocida),
los avisos de cabeceras del Nginx local, los dos falsos positivos de Gitleaks
ni las CVE sin ruta explotable demostrada.

### Reproducción Y Estado De Cierre

El runner versionado scripts/run-security-assessment.ps1 fija tienda-perf,
ejecuta el baseline ZAP, el escaneo OpenAPI en modo seguro y los casos
authorization-matrix.ps1/security-regression.ps1. La última ejecución pasó y
guardó JSON pasivos fuera de OneDrive en
%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts\security-20260924T054228Z.
No hay contraseña ni JWT en el Markdown. El plan
tests/security/zap/automation-authenticated.yaml se ejecutó después de la
autorización expresa y quedó limitado a cuatro GET.
No se escanearon activamente las 157 escrituras potenciales de OpenAPI.
Las carreras se forzaron sólo en PostgreSQL sintético y no deben repetirse
contra entornos compartidos o productivos.

El usuario aprobó corregir primero SEC-001 y SEC-002. El plan separado está
en `docs/superpowers/plans/2026-09-24-security-remediation-sec001-sec002.md`;
SEC-003–005 permanecen documentados pero fuera de esta corrección. Las dos
pruebas de regresión fallaron sobre el código previo: 200/200 en el conteo y
esperado 100 frente a 160 tras un pago en efectivo. Después del parche,
pasaron las dos regresiones y la clase completa de concurrencia, incluido un
caso de devolución en efectivo que comparte la frontera de cierre. La suite
backend final pasó con 345 pruebas, cero fallos y cero omitidas, incluido el
control de pago posterior al cierre. Los commits `bc84eb4` y `a4d8f0c`
separan ambos hallazgos. El escaneo formal del diff
`da1d30618609371014ef0f849d468e4833e68353..a4d8f0c19184193ec865dcee75f97671f5f5bcc5`
terminó con cobertura completa de los tres archivos productivos cambiados y
cero hallazgos nuevos (scan `39d42195-80a6-442f-bed5-755f139a42ed`). El
reporte canónico está bajo
`%USERPROFILE%\.codex\state\plugins\codex-security\scans\tienda-abarrotes\a4d8f0c19184193ec865dcee75f97671f5f5bcc5_20260925T040947Z_h3c8md76\report.md`.
La revisión cubre ese diff, no SEC-003–005 ni todo el repositorio.

La repetición de Load con el backend corregido terminó `PASSED` en el manifiesto
`load/20260925T035023696Z-campaign.json`: 154 874 solicitudes, p95 147,87 ms,
p99 299,35 ms, tasa de errores HTTP 0, checks 100%, cero errores funcionales o
5xx inesperados y todas las invariantes SQL en `PASS`. La línea base de tres
corridas registró p95 210,57 / 98,13 / 92,53 ms y p99 373,36 / 187,78 /
199,87 ms. La nueva cifra queda entre la corrida fría y las calientes; una sola
repetición, con otros contenedores del equipo activos y una imagen PostgreSQL
reconstruida, no permite atribuir una mejora o regresión causal a los bloqueos.

## Evidencia Y Pendientes

Artefactos bajo `%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts`.
El primer laboratorio se cerró el 16 de septiembre. El segundo laboratorio de
seguridad se cerró el 24 de septiembre. El tercer laboratorio aislado
se usó para ZAP autenticado, prueba fiscal y remediación y se cerró tras
conservar la evidencia local para
trazabilidad; no se publicó ni se incluyó en Git.
`stress/20260905T061951941Z` se interrumpió antes de Soak: su manifiesto conserva
`RUNNING`, pero el proceso terminó. No confundirlo con ejecución activa.

- Si se requiere descartar fugas de largo plazo, ampliar duración y analizar memoria retenida/GC.
- SEC-001/002 están verificados en el diff y en pruebas locales; mantener seguimiento de cualquier nueva regresión.
- SEC-003–005 requieren una decisión de alcance posterior; no se corrigen en esta fase.
