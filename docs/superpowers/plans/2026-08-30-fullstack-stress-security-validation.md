# Plan Maestro De Estrés Y Seguridad Fullstack

> **Para la ejecución:** usar `superpowers:executing-plans` y trabajar tarea por tarea en la sesión principal. No crear subagentes ni hilos auxiliares. Las casillas `- [ ]` registran el avance y cada fase termina con un punto de control verificable.

**Objetivo:** medir la capacidad, degradación y recuperación del Angular Web y del backend Spring Boot en un ambiente desechable, documentar los resultados y después buscar vulnerabilidades con controles estáticos, dinámicos y de lógica de negocio antes de planear correcciones.

**Arquitectura:** separar la validación en dos campañas. La primera usa `k6` para concurrencia sobre la API y Playwright/Lighthouse para rendimiento real del navegador; la segunda usa los controles existentes de cadena de suministro, un escaneo Codex Security y OWASP ZAP contra el mismo ambiente aislado. PostgreSQL, Redis y backend se ejecutan con un nombre de proyecto Docker exclusivo para que ningún escenario modifique la base local habitual.

**Stack:** Angular 22, Playwright, Lighthouse CI, Java 21, Spring Boot 4.1, PostgreSQL 18, Redis 8.8, Docker Compose, k6, Micrometer/Prometheus, Gitleaks, Trivy, npm audit, Gradle y OWASP ZAP.

**Especificación:** este documento integra el alcance, diseño, criterios de aceptación y plan de ejecución de la campaña solicitada.

## Control Del Documento

| Campo         | Valor                 |
| ------------- | --------------------- |
| Proyecto      | Tienda de Abarrotes   |
| Código        | TDA-QA-PLAN-001       |
| Versión       | v1.4                  |
| Estado        | SEC-001/002 remediados; SEC-003–005 pendientes de decisión |
| Responsable   | ODCC / Codex          |
| Fecha         | 2026-09-24            |
| Clasificación | Interno               |

## Control De Versiones

| Versión | Fecha      | Autor        | Cambio                                 | Estado                   |
| ------- | ---------- | ------------ | -------------------------------------- | ------------------------ |
| v0.1    | 2026-08-30 | ODCC / Codex | Diseño inicial de la campaña fullstack | Borrador para aprobación |
| v1.0    | 2026-08-30 | ODCC / Codex | Aprobación e inicio de ejecución       | Aprobado                 |
| v1.1    | 2026-08-30 | ODCC / Codex | Secretos y evidencia cruda fuera de OneDrive | En ejecución          |
| v1.2 | 2026-09-16 | Codex | Tarea 6 verificada y primer informe entregable; revisión pendiente | En ejecución |
| v1.3 | 2026-09-24 | Codex | Tareas 8–11 documentadas; ZAP autenticado automatizado y priorización pendientes | En revisión |
| v1.4 | 2026-09-24 | Codex | SEC-001/002 en commits separados; regresiones, Load y diff scan verificados | En revisión |

## 1. Decisión Técnica

Se adopta un enfoque especializado por capa:

| Superficie             | Herramienta                                         | Qué medirá                                                                             |
| ---------------------- | --------------------------------------------------- | -------------------------------------------------------------------------------------- |
| API backend            | k6                                                  | throughput, latencia, errores, concurrencia, punto de quiebre y recuperación           |
| Angular en navegador   | Playwright + Chrome DevTools Protocol               | navegación repetida, errores de consola, memoria, tiempos de interacción y estabilidad |
| Angular construido     | Lighthouse CI                                       | LCP, CLS, accesibilidad, buenas prácticas y presupuesto del bundle                     |
| Backend y contenedores | Actuator/Prometheus + Docker stats                  | heap, CPU, conexiones, threads, GC, Hikari y salud                                     |
| PostgreSQL             | `pg_stat_activity`, `pg_stat_database` y locks      | saturación, sesiones, rollbacks, bloqueos y consultas lentas                           |
| Código y dependencias  | Codex Security, Gitleaks, Trivy, npm audit y Gradle | vulnerabilidades de código, secretos, CVE y configuración                              |
| API en ejecución       | OWASP ZAP + pruebas HTTP dirigidas                  | autenticación, autorización, inyección, headers, CORS y exposición                     |

No se usará JMeter para simular el navegador: genera tráfico HTTP, pero no representa renderizado, memoria, Signals, Angular Material ni interacción real. Tampoco se abrirán cientos de Chromium en una laptop; el volumen concurrente se aplicará sobre la API y el frontend se someterá a ciclos controlados de navegador mientras el backend está bajo carga.

## 2. Reglas De Seguridad De La Campaña

- Las pruebas se ejecutarán únicamente contra `localhost` y un proyecto Docker llamado `tienda-perf`.
- Está prohibido apuntar k6, ZAP o scripts ofensivos a producción, repositorios de terceros o direcciones públicas.
- La base de pruebas será efímera y se eliminará con `docker compose down -v` al terminar.
- Se usarán usuarios, clientes, productos, lotes y ventas ficticios; nunca datos personales reales.
- Las credenciales se generarán localmente bajo `%LOCALAPPDATA%\TiendaAbarrotes\performance`, fuera de Git y de OneDrive.
- El login se ejecutará solo en preparación; los escenarios normales reutilizarán tokens para no convertir el rate limiter en el cuello de botella equivocado.
- El rate limiting tendrá escenarios propios y separados.
- Toda prueba de escritura usará UUID e idempotency keys nuevos o deterministas según el caso.
- Si aparecen corrupción de datos, pérdida de invariantes, crecimiento de memoria sin recuperación, más de 5 % de respuestas `5xx` o indisponibilidad sostenida, la fase se detendrá y se conservará evidencia.
- La campaña de vulnerabilidades buscará y validará hallazgos; no se implementarán correcciones hasta presentar el informe y acordar prioridades.

## 3. Archivos Previstos

```text
%LOCALAPPDATA%/TiendaAbarrotes/performance/
├── perf.env                         # secretos locales efímeros
└── artifacts/                       # evidencia cruda no versionada

tests/performance/
├── compose.performance.yaml         # servicios k6 y web estática
├── README.md
├── k6/
│   ├── lib/config.js
│   ├── lib/auth.js
│   ├── lib/checks.js
│   ├── backend-smoke.js
│   ├── backend-load.js
│   ├── backend-stress.js
│   ├── backend-spike.js
│   └── backend-soak.js
└── web/
    ├── frontend-performance.spec.ts
    └── lighthouserc.json

tests/security/
├── README.md
├── zap/
│   ├── automation-baseline.yaml
│   └── automation-authenticated.yaml
└── cases/
    ├── authorization-matrix.ps1
    ├── injection-payloads.json
    └── security-regression.ps1

scripts/
├── perf-environment-up.ps1
├── perf-environment-down.ps1
├── run-fullstack-stress.ps1
└── run-security-assessment.ps1

docs/reports/quality/
└── 2026-08-30-fullstack-stress-security-analysis.md
```

Los resultados crudos se escribirán fuera de OneDrive bajo `%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts\<timestamp>`. El Markdown consolidado solo contendrá cifras, tablas, gráficas pequeñas, evidencia sanitizada y conclusiones. `artifacts/quality/` permanecerá ignorado como defensa ante copias accidentales.

## 4. Perfiles De Carga Iniciales

Los umbrales se calibrarán con la línea base del mismo equipo, pero la primera ejecución utilizará estos perfiles reproducibles:

| Perfil |                        Carga |   Duración | Propósito                                               |
| ------ | ---------------------------: | ---------: | ------------------------------------------------------- |
| Smoke  |            1 usuario virtual |   1 minuto | validar datos, autenticación y checks                   |
| Load   |        10 → 25 → 50 usuarios | 12 minutos | comportamiento dentro de capacidad esperada             |
| Stress | 50 → 75 → 100 → 150 usuarios | 16 minutos | localizar degradación y primer umbral incumplido        |
| Spike  |         0 → 150 → 0 usuarios |  4 minutos | reacción ante incremento súbito                         |
| Soak   |                  25 usuarios | 30 minutos | fugas, acumulación de conexiones y degradación temporal |

La mezcla inicial de backend será:

- 55 % consultas: productos, stock, clientes, ventas y reportes;
- 20 % operaciones de catálogo/contexto;
- 15 % ventas idempotentes sobre datos reservados para la campaña;
- 5 % pagos no efectivos para evitar depender de una caja compartida;
- 5 % Sync/checkpoints con secuencias exclusivas por usuario virtual.

Las escrituras no compartirán el mismo producto, venta o secuencia salvo en escenarios diseñados específicamente para probar concurrencia.

## 5. Criterios De Aceptación Iniciales

### Backend

| Indicador                   |                             Load |                                 Stress/Spike |
| --------------------------- | -------------------------------: | -------------------------------------------: |
| Respuestas `5xx`            |                              0 % |             < 1 % antes del punto de quiebre |
| Errores inesperados totales |                            < 1 % |             < 3 % antes del punto de quiebre |
| p95 de lecturas             |                         ≤ 500 ms |                        registrar degradación |
| p95 de escrituras           |                         ≤ 900 ms |                        registrar degradación |
| p99 global                  |                          ≤ 1.5 s |                        registrar degradación |
| Readiness                   |                     siempre `UP` |                   debe recuperarse en ≤ 60 s |
| Pool Hikari                 |                 < 80 % sostenido | sin conexiones perdidas después de la prueba |
| Integridad                  | sin stock negativo ni duplicados |      obligatoria incluso después del quiebre |

Los `409` funcionales y `429` de escenarios explícitos no se contarán como fallos si el código, mensaje y efecto son los esperados.

### Angular Web

| Indicador                          |                                                  Umbral |
| ---------------------------------- | ------------------------------------------------------: |
| Errores de consola/excepciones     |                                                       0 |
| LCP                                |                                                 ≤ 2.5 s |
| CLS                                |                                                  ≤ 0.10 |
| Lighthouse Performance             |                                                    ≥ 85 |
| Lighthouse Accessibility           |                                                    ≥ 95 |
| Lighthouse Best Practices          |                                                    ≥ 90 |
| Navegación interna p95             |                    ≤ 1 s en ambiente local estabilizado |
| Crecimiento de heap tras 50 ciclos |                   ≤ 20 % después de forzar GC de prueba |
| Build inicial                      | no superar el presupuesto configurado en `angular.json` |

Los valores no se presentarán como capacidad productiva: describen el equipo y contenedores usados durante la campaña. El informe incluirá CPU, RAM, versiones y condiciones para que las cifras sean reproducibles.

## 6. Plan De Ejecución

### Tarea 1: Preparar Rama Y Ambiente Aislado

**Archivos:** `.gitignore`, `tests/performance/compose.performance.yaml`, `scripts/perf-environment-up.ps1`, `scripts/perf-environment-down.ps1`.

- [x] Crear la rama `test/fullstack-stress-security` desde la revisión aprobada.
- [x] Verificar que el árbol esté limpio y registrar commit, Java, Node, npm, Docker, CPU y RAM.
- [x] Añadir `.performance/` y `artifacts/quality/` a `.gitignore`.
- [x] Crear el overlay Compose con puertos separados, volumen PostgreSQL exclusivo y servicios de herramientas fijados por versión/digest; nunca usar etiquetas `latest`.
- [x] Generar secretos aleatorios fuera de OneDrive sin imprimirlos en logs ni entregarlos completos a k6.
- [x] Construir `bootJar`, aplicar Flyway desde cero, cargar fixtures sintéticos y comprobar readiness.
- [x] Ejecutar `docker compose --project-name tienda-perf ... down -v` y confirmar que el ambiente habitual no cambió.
- [x] Commit: `test(performance): add isolated load test environment` (`ae4f847`).

**Aceptación:** levantar y destruir `tienda-perf` dos veces produce el mismo esquema y no modifica los volúmenes habituales.

### Tarea 2: Crear Fixtures Y Contratos De Prueba

**Archivos:** `database/seeds/performance/`, `tests/performance/k6/lib/` y `tests/performance/README.md`.

- [x] Crear sucursales, almacenes, cajas, usuarios y productos ficticios con IDs conocidos solo dentro del ambiente efímero.
- [x] Separar datos por escenario para evitar contención accidental.
- [ ] Implementar login de preparación, renovación de token y lectura de contexto. Login y renovación en `setup()` verificados; el harness usa el contexto fijo de fixtures, pero no hace una lectura explícita del contexto autenticado.
- [x] Definir checks compartidos para `status`, `code`, `reason`, `correlationId`, esquema y tiempo.
- [x] Validar scripts con `k6 inspect` y ejecutar Smoke hasta obtener 100 % de checks.
- [x] Commit: `test(performance): add deterministic load fixtures` (`c939495`).

**Aceptación:** Smoke puede repetirse tres veces sin conflictos de idempotencia ni crecimiento de datos inesperado.

### Tarea 3: Medir Línea Base

- [ ] Ejecutar suites actuales de backend y Angular antes de aplicar carga. Angular pasó; Gradle informó `:test UP-TO-DATE` en ese momento. Los 341 tests backend se reejecutaron después, no antes.
- [x] Generar build Angular de producción y registrar tamaños.
- [x] Registrar durante cinco minutos métricas ociosas de Actuator, Prometheus, Docker y PostgreSQL.
- [x] Ejecutar 20 peticiones secuenciales por endpoint crítico para calentar JVM y caches.
- [x] Repetir la medición y guardar la mediana como línea base.
- [x] Añadir la configuración física y software al informe Markdown.

**Aceptación:** existe una línea base sin carga que permite distinguir problemas previos de los provocados por la campaña.

### Tarea 4: Implementar Carga Backend

**Archivos:** `tests/performance/k6/backend-smoke.js` y `backend-load.js`.

- [x] Crear primero checks que fallen cuando exista `5xx`, falta de correlation id o latencia fuera del umbral.
- [x] Ejecutar Smoke y confirmar que detecta deliberadamente una URL inválida; restaurar la URL y confirmar verde.
- [x] Implementar la mezcla de lectura/escritura con `setup()` para tokens y datos.
- [ ] Etiquetar métricas por módulo, endpoint, operación y resultado funcional. Los tres primeros son etiquetas k6; el resultado funcional se conserva en checks/contadores, no como etiqueta estable.
- [x] Ejecutar Load tres veces con un enfriamiento de cinco minutos.
- [x] Conservar la mediana; no escoger la mejor ejecución.
- [x] Commit: `test(performance): add backend load scenarios` (`22b0530`).

**Aceptación:** las tres corridas terminan sin `5xx`, conservan invariantes y generan summaries JSON comparables.

### Tarea 5: Ejecutar Stress, Spike Y Soak

**Archivos:** `backend-stress.js`, `backend-spike.js`, `backend-soak.js` y `tests/performance/scripts/run-fullstack-stress.ps1`.

- [x] Automatizar captura de métricas antes, durante y después de cada perfil.
- [x] Ejecutar Stress hasta el primer umbral incumplido, sin elevar carga después de un criterio de parada.
- [x] Ejecutar Spike y medir tiempo de recuperación.
- [x] Reiniciar el ambiente para descartar contaminación.
- [x] Ejecutar Soak durante 30 minutos y comparar heap, conexiones y threads iniciales/finales.
- [x] Ejecutar consultas de integridad: stock negativo, pagos duplicados, idempotencia, secuencias Sync y movimientos huérfanos.
- [x] Commit: `test(performance): add stress spike and soak profiles` (`f7d9f4d`).

**Aceptación:** se identifica el último escalón estable y el sistema recupera readiness sin corrupción transaccional.

### Tarea 6: Medir Angular Web

**Archivos:** `tests/performance/web/frontend-performance.spec.ts`, `lighthouserc.json`, `apps/web/package.json`.

- [x] Servir `dist/tienda-web/browser` después de `npm run build`; no medir `ng serve` de desarrollo.
- [x] Añadir tres ejecuciones Lighthouse sobre login, dashboard y catálogo y usar la mediana.
- [x] Implementar Playwright para 50 ciclos de navegación, filtros, modo oscuro, tablas y apertura/cierre de diálogos.
- [x] Capturar heap, nodos DOM, long tasks, errores de consola y `pageerror` por ciclo.
- [x] Repetir en escritorio, tablet y móvil emulado.
- [x] Ejecutar el flujo Playwright mientras k6 mantiene 25 usuarios sobre backend.
- [x] Verificar que errores controlados siguen mostrando correlation id bajo carga.
- [x] Commit: `test(web): add browser performance scenarios` (`9ae76ae`).

**Aceptación:** Angular cumple umbrales o el informe identifica ruta, interacción y métrica exacta que falla; no hay errores de consola.

### Tarea 7: Elaborar El Primer Análisis

**Archivo:** `docs/reports/quality/2026-08-30-fullstack-stress-security-analysis.md`.

- [x] Consolidar línea base, Load, Stress, Spike, Soak, Lighthouse y Playwright.
- [x] Incluir percentiles, throughput, tasa de error, consumo, saturación, recuperación y condiciones del equipo.
- [x] Diferenciar cuello de botella del navegador, aplicación, JVM, pool, PostgreSQL y Redis.
- [x] Clasificar cada observación como confirmada, hipótesis o limitación de laboratorio.
- [x] Incluir evidencias reproducibles sin tokens, contraseñas ni datos sensibles.
- [x] Detenerse para revisión del usuario antes de iniciar pruebas ofensivas dinámicas.

**Aceptación:** el usuario puede leer un único Markdown y entender capacidad estable, punto de degradación, punto de quiebre y riesgos sin abrir los JSON crudos.

### Tarea 8: Definir Alcance De Seguridad Y Modelo De Amenazas

- [x] Confirmar por escrito que el único objetivo autorizado es el ambiente `tienda-perf` local.
- [x] Ejecutar un escaneo estándar Codex Security de repositorio completo con cobertura de Angular, backend, SQL, Docker, CI y configuración.
- [x] Modelar activos: JWT, credenciales, información fiscal, inventario, pagos, Sync y acceso por sucursal.
- [x] Enumerar fronteras de confianza y actores: anónimo, usuario autenticado, cajero, almacén, administrador y dispositivo.
- [x] Definir casos OWASP ASVS/API Top 10 aplicables y excluir pruebas destructivas externas.

**Aceptación:** cada prueba posterior se vincula a un activo, frontera, amenaza y resultado esperado.

### Tarea 9: Ejecutar Análisis Estático Y De Cadena De Suministro

- [x] Ejecutar Gitleaks sobre historial completo.
- [x] Ejecutar Trivy sobre filesystem, configuración y la imagen backend construida.
- [x] Ejecutar `npm audit` y revisar dependencias runtime y desarrollo por separado.
- [x] Revisar el grafo Gradle y CVE de dependencias transitivas.
- [x] Revisar bundles Angular para secretos, URLs privadas y source maps no intencionales.
- [x] Revisar Dockerfiles, Compose, workflows, permisos de GitHub y acciones fijadas por SHA.
- [x] Validar manualmente cada candidato antes de declararlo hallazgo.

**Aceptación:** cada hallazgo conserva archivo/línea, causa raíz, impacto, severidad, confianza y evidencia; los falsos positivos quedan descartados explícitamente.

### Tarea 10: Ejecutar DAST Y Casos Dirigidos

**Archivos:** `tests/security/zap/` y `tests/security/cases/`.

- [x] Ejecutar ZAP baseline sobre Angular y OpenAPI sin autenticación.
- [x] Ejecutar ZAP autenticado con un usuario de privilegios mínimos contra la base efímera. Plan automatizado de cuatro GET pasivos completado con JWT sintético tras autorización expresa.
- [x] Probar manualmente `401`, `403`, acceso horizontal entre sucursales e IDOR con UUID ajenos.
- [x] Probar JWT alterado, expirado, revocado y con `auth_version` anterior.
- [x] Probar SQL/JSON injection, XSS reflejado/almacenado, mass assignment, CORS, headers, payloads grandes y profundidad Sync.
- [x] Probar rate limiting por IP/cuenta y caída controlada de Redis en el perfil distribuido.
- [x] Probar concurrencia ofensiva sobre ventas, pagos, devoluciones, cierre de caja, recepciones e idempotencia.
- [x] Confirmar que no se registran tokens, contraseñas ni payloads fiscales completos en los logs inspeccionados. Se creó un perfil fiscal sintético 201 y no aparecieron su marcador, RFC, payload completo ni JWT en logs de backend o PostgreSQL durante esa operación.

**Aceptación:** las pruebas no escapan de localhost, no usan datos reales y toda vulnerabilidad reproducible cuenta con solicitud mínima, respuesta, correlation id e impacto demostrado de forma segura.

### Tarea 11: Consolidar Hallazgos De Seguridad

**Archivo:** el mismo informe `docs/reports/quality/2026-08-30-fullstack-stress-security-analysis.md`.

- [x] Añadir resumen ejecutivo con conteo por severidad y confianza.
- [x] Documentar cobertura real y zonas no verificadas.
- [x] Para cada hallazgo incluir ID, CWE/OWASP, superficie, precondición, pasos de reproducción sanitizados, impacto, evidencia y recomendación.
- [x] Relacionar vulnerabilidades con comportamientos observados bajo estrés cuando aplique.
- [x] Ordenar remediación por criticidad, explotabilidad y costo.
- [x] No modificar código productivo durante esta tarea.
- [x] Presentar el informe al usuario y acordar qué hallazgos se corregirán primero. El usuario confirmó SEC-001 y SEC-002 antes que SEC-003–005.

**Aceptación:** no se reportan sospechas como vulnerabilidades confirmadas y el documento permite crear una tarea de corrección independiente por hallazgo.

### Tarea 12: Preparar La Fase De Corrección

- [x] Crear un plan separado de remediación únicamente para hallazgos validados y aprobados: `2026-09-24-security-remediation-sec001-sec002.md`.
- [x] Escribir primero una prueba de regresión por vulnerabilidad. SEC-001 y SEC-002 fallaron sobre el código previo; se añadió también la ruta de devolución en efectivo.
- [x] Corregir en commits pequeños sin mezclar hallazgos. SEC-001: `bc84eb4`; SEC-002: `a4d8f0c`.
- [x] Repetir el caso ofensivo, la suite del módulo y el escaneo de verificación. Las carreras y controles pasaron, la suite backend sumó 345 pruebas verdes y el diff scan `39d42195-80a6-442f-bed5-755f139a42ed` no encontró hallazgos nuevos en los tres archivos cambiados.
- [x] Repetir Load si la corrección toca consultas, filtros, autenticación o transacciones. Una corrida aislada de 12 minutos pasó: 154 874 solicitudes, p95 147,87 ms, errores HTTP 0 e invariantes SQL PASS; comparación orientativa con tres corridas históricas.

**Aceptación:** cada corrección demuestra que el ataque deja de funcionar sin romper la capacidad o funcionalidad existente.

## 7. Contenido Del Informe Final

El informe que recibirá el usuario tendrá esta estructura:

1. Resumen ejecutivo.
2. Alcance, fecha, revisión Git y ambiente.
3. Hardware y versiones.
4. Metodología y limitaciones.
5. Línea base.
6. Resultados backend: Load, Stress, Spike y Soak.
7. Resultados Angular: build, Lighthouse, interacción, memoria y carga combinada.
8. Cuellos de botella y análisis de causa.
9. Resultados de seguridad estática, dependencias y contenedores.
10. Resultados DAST y lógica de negocio.
11. Hallazgos priorizados.
12. Recomendaciones de capacidad y seguridad.
13. Plan de corrección propuesto.
14. Evidencias y comandos de reproducción.

## 8. Riesgos

| Código  | Riesgo                                        | Probabilidad | Impacto | Mitigación                                                             |
| ------- | --------------------------------------------- | -----------: | ------: | ---------------------------------------------------------------------- |
| RIE-001 | Probar accidentalmente la base habitual       |         Baja |    Alto | proyecto Compose y volumen exclusivos, preflight de URL y project name |
| RIE-002 | Confundir capacidad de laptop con producción  |         Alta |   Medio | registrar hardware y declarar resultados relativos                     |
| RIE-003 | Rate limiter invalida la mezcla normal        |        Media |   Medio | login solo en setup y escenario de rate limit separado                 |
| RIE-004 | Datos compartidos producen conflictos falsos  |        Media |    Alto | particionar datos por usuario virtual                                  |
| RIE-005 | ZAP ejecuta operaciones destructivas          |        Media |    Alto | base efímera, rutas permitidas y criterios de parada                   |
| RIE-006 | Falsos positivos de seguridad                 |         Alta |    Alto | validación fuente-a-sumidero y reproducción mínima                     |
| RIE-007 | Artefactos contienen secretos                 |         Baja |    Alto | sanitización y artefactos ignorados por Git                            |
| RIE-008 | Soak queda afectado por suspensión de Windows |        Media |   Medio | energía conectada, suspensión deshabilitada y ejecución supervisada    |

## 9. Comandos De Validación Global

```powershell
Set-Location "C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes\apps\backend"
.\gradlew.bat test --no-daemon
.\gradlew.bat bootJar --no-daemon

Set-Location ..\web
npm ci
npm test -- --watch=false
npm run build
npm run e2e
npm audit --omit=dev

Set-Location ..\..
.\scripts\perf-environment-down.ps1
```

Los comandos específicos de k6, Lighthouse y ZAP se incorporarán a los scripts versionados; el operador no tendrá que reconstruir parámetros manualmente.

## 10. Criterio De Cierre

La campaña se considera terminada únicamente cuando:

- [x] el entorno aislado puede crearse y destruirse de forma repetible;
- [x] los cinco perfiles de rendimiento tienen evidencia;
- [x] Angular fue medido con build de producción y navegador real;
- [x] se verificó integridad después de carga y concurrencia;
- [x] el escaneo estático y DAST cubrieron las superficies autorizadas; ZAP se limitó al modo pasivo y a cuatro GET autenticados, sin escaneo activo ni escrituras;
- [x] cada hallazgo está validado o descartado;
- [x] el informe Markdown consolidado está completo y sanitizado;
- [x] el usuario confirmó la lectura del informe y priorizó SEC-001/002 antes de iniciar correcciones;
- [x] no quedaron servicios, volúmenes, credenciales ni artefactos sensibles activos.

## 11. Commits Recomendados

```text
test(performance): add isolated load test environment
test(performance): add deterministic load fixtures
test(performance): add backend load scenarios
test(performance): add stress spike and soak profiles
test(web): add browser performance scenarios
test(security): add authorized DAST scenarios
docs(quality): add fullstack stress and security analysis
```

## 12. Siguiente Acción Tras La Aprobación

Comenzar por la Tarea 1 y detenerse al finalizar la Tarea 7 para entregar el primer análisis de estrés. Solo después de que el usuario lo lea se ejecutarán las Tareas 8 a 11 de seguridad. Las correcciones pertenecen a una campaña posterior derivada de los hallazgos validados.

## 13. Punto De Control Del 16 De Septiembre

El primer informe de rendimiento está en
`docs/reports/quality/2026-08-30-fullstack-stress-security-analysis.md`, versión v0.2.
Consolida la evidencia de preparación, Smoke, baseline, tres Load, Stress, Spike,
Soak y navegador. Documenta expresamente las adaptaciones de duración de Smoke y
Spike, el descenso ignorado en Stress original y la ausencia del log causal del
5xx. Las casillas históricas de las Tareas 1–5 no sustituyen esta evidencia ni
suponen equivalencia exacta con todos los perfiles inicialmente propuestos.

Verificación posterior: backend 341 tests y Angular 152 tests aprobados;
build Angular reproducido con 59 archivos idénticos a la medición. Se cerró
`tienda-perf` y se eliminaron sus credenciales; la evidencia queda fuera de Git.
Las Tareas 8–12 no han comenzado. El siguiente paso depende de la revisión del
usuario, no de una nueva ejecución automática de pruebas.
