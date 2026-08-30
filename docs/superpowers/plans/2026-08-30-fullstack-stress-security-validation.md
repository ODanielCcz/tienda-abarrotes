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
| Versión       | v1.0                  |
| Estado        | Aprobado en ejecución |
| Responsable   | ODCC / Codex          |
| Fecha         | 2026-08-30            |
| Clasificación | Interno               |

## Control De Versiones

| Versión | Fecha      | Autor        | Cambio                                 | Estado                   |
| ------- | ---------- | ------------ | -------------------------------------- | ------------------------ |
| v0.1    | 2026-08-30 | ODCC / Codex | Diseño inicial de la campaña fullstack | Borrador para aprobación |
| v1.0    | 2026-08-30 | ODCC / Codex | Aprobación e inicio de ejecución       | Aprobado                 |

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
- Las credenciales se generarán localmente en `.performance/perf.env`, archivo ignorado por Git.
- El login se ejecutará solo en preparación; los escenarios normales reutilizarán tokens para no convertir el rate limiter en el cuello de botella equivocado.
- El rate limiting tendrá escenarios propios y separados.
- Toda prueba de escritura usará UUID e idempotency keys nuevos o deterministas según el caso.
- Si aparecen corrupción de datos, pérdida de invariantes, crecimiento de memoria sin recuperación, más de 5 % de respuestas `5xx` o indisponibilidad sostenida, la fase se detendrá y se conservará evidencia.
- La campaña de vulnerabilidades buscará y validará hallazgos; no se implementarán correcciones hasta presentar el informe y acordar prioridades.

## 3. Archivos Previstos

```text
.performance/
└── perf.env                         # local, ignorado por Git

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

Los resultados pesados se escribirán bajo `artifacts/quality/<timestamp>/` y se agregarán a `.gitignore`. El Markdown consolidado solo contendrá cifras, tablas, gráficas pequeñas, evidencia sanitizada, conclusiones y enlaces relativos a los artefactos locales.

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

- [ ] Crear la rama `test/fullstack-stress-security` desde la revisión aprobada.
- [ ] Verificar que el árbol esté limpio y registrar commit, Java, Node, npm, Docker, CPU y RAM.
- [ ] Añadir `.performance/` y `artifacts/quality/` a `.gitignore`.
- [ ] Crear el overlay Compose con puertos separados, volumen PostgreSQL exclusivo y servicios de herramientas fijados por versión/digest; nunca usar etiquetas `latest`.
- [ ] Generar secretos aleatorios en `.performance/perf.env` sin imprimirlos en logs.
- [ ] Construir `bootJar`, aplicar Flyway desde cero, cargar fixtures sintéticos y comprobar readiness.
- [ ] Ejecutar `docker compose --project-name tienda-perf ... down -v` y confirmar que el ambiente habitual no cambió.
- [ ] Commit: `test(performance): add isolated load test environment`.

**Aceptación:** levantar y destruir `tienda-perf` dos veces produce el mismo esquema y no modifica los volúmenes habituales.

### Tarea 2: Crear Fixtures Y Contratos De Prueba

**Archivos:** `database/seeds/performance/`, `tests/performance/k6/lib/` y `tests/performance/README.md`.

- [ ] Crear sucursales, almacenes, cajas, usuarios y productos ficticios con IDs conocidos solo dentro del ambiente efímero.
- [ ] Separar datos por escenario para evitar contención accidental.
- [ ] Implementar login de preparación, renovación de token y lectura de contexto.
- [ ] Definir checks compartidos para `status`, `code`, `reason`, `correlationId`, esquema y tiempo.
- [ ] Validar scripts con `k6 inspect` y ejecutar Smoke hasta obtener 100 % de checks.
- [ ] Commit: `test(performance): add deterministic load fixtures`.

**Aceptación:** Smoke puede repetirse tres veces sin conflictos de idempotencia ni crecimiento de datos inesperado.

### Tarea 3: Medir Línea Base

- [ ] Ejecutar suites actuales de backend y Angular antes de aplicar carga.
- [ ] Generar build Angular de producción y registrar tamaños.
- [ ] Registrar durante cinco minutos métricas ociosas de Actuator, Prometheus, Docker y PostgreSQL.
- [ ] Ejecutar 20 peticiones secuenciales por endpoint crítico para calentar JVM y caches.
- [ ] Repetir la medición y guardar la mediana como línea base.
- [ ] Añadir la configuración física y software al informe Markdown.

**Aceptación:** existe una línea base sin carga que permite distinguir problemas previos de los provocados por la campaña.

### Tarea 4: Implementar Carga Backend

**Archivos:** `tests/performance/k6/backend-smoke.js` y `backend-load.js`.

- [ ] Crear primero checks que fallen cuando exista `5xx`, falta de correlation id o latencia fuera del umbral.
- [ ] Ejecutar Smoke y confirmar que detecta deliberadamente una URL inválida; restaurar la URL y confirmar verde.
- [ ] Implementar la mezcla de lectura/escritura con `setup()` para tokens y datos.
- [ ] Etiquetar métricas por módulo, endpoint, operación y resultado funcional.
- [ ] Ejecutar Load tres veces con un enfriamiento de cinco minutos.
- [ ] Conservar la mediana; no escoger la mejor ejecución.
- [ ] Commit: `test(performance): add backend load scenarios`.

**Aceptación:** las tres corridas terminan sin `5xx`, conservan invariantes y generan summaries JSON comparables.

### Tarea 5: Ejecutar Stress, Spike Y Soak

**Archivos:** `backend-stress.js`, `backend-spike.js`, `backend-soak.js` y `scripts/run-fullstack-stress.ps1`.

- [ ] Automatizar captura de métricas antes, durante y después de cada perfil.
- [ ] Ejecutar Stress hasta el primer umbral incumplido, sin elevar carga después de un criterio de parada.
- [ ] Ejecutar Spike y medir tiempo de recuperación.
- [ ] Reiniciar el ambiente para descartar contaminación.
- [ ] Ejecutar Soak durante 30 minutos y comparar heap, conexiones y threads iniciales/finales.
- [ ] Ejecutar consultas de integridad: stock negativo, pagos duplicados, idempotencia, secuencias Sync y movimientos huérfanos.
- [ ] Commit: `test(performance): add stress spike and soak profiles`.

**Aceptación:** se identifica el último escalón estable y el sistema recupera readiness sin corrupción transaccional.

### Tarea 6: Medir Angular Web

**Archivos:** `tests/performance/web/frontend-performance.spec.ts`, `lighthouserc.json`, `apps/web/package.json`.

- [ ] Servir `dist/tienda-web/browser` después de `npm run build`; no medir `ng serve` de desarrollo.
- [ ] Añadir tres ejecuciones Lighthouse sobre login, dashboard y catálogo y usar la mediana.
- [ ] Implementar Playwright para 50 ciclos de navegación, filtros, modo oscuro, tablas y apertura/cierre de diálogos.
- [ ] Capturar heap, nodos DOM, long tasks, errores de consola y `pageerror` por ciclo.
- [ ] Repetir en escritorio, tablet y móvil emulado.
- [ ] Ejecutar el flujo Playwright mientras k6 mantiene 25 usuarios sobre backend.
- [ ] Verificar que errores controlados siguen mostrando correlation id bajo carga.
- [ ] Commit: `test(web): add browser performance scenarios`.

**Aceptación:** Angular cumple umbrales o el informe identifica ruta, interacción y métrica exacta que falla; no hay errores de consola.

### Tarea 7: Elaborar El Primer Análisis

**Archivo:** `docs/reports/quality/2026-08-30-fullstack-stress-security-analysis.md`.

- [ ] Consolidar línea base, Load, Stress, Spike, Soak, Lighthouse y Playwright.
- [ ] Incluir percentiles, throughput, tasa de error, consumo, saturación, recuperación y condiciones del equipo.
- [ ] Diferenciar cuello de botella del navegador, aplicación, JVM, pool, PostgreSQL y Redis.
- [ ] Clasificar cada observación como confirmada, hipótesis o limitación de laboratorio.
- [ ] Incluir evidencias reproducibles sin tokens, contraseñas ni datos sensibles.
- [ ] Detenerse para revisión del usuario antes de iniciar pruebas ofensivas dinámicas.

**Aceptación:** el usuario puede leer un único Markdown y entender capacidad estable, punto de degradación, punto de quiebre y riesgos sin abrir los JSON crudos.

### Tarea 8: Definir Alcance De Seguridad Y Modelo De Amenazas

- [ ] Confirmar por escrito que el único objetivo autorizado es el ambiente `tienda-perf` local.
- [ ] Ejecutar un escaneo estándar Codex Security de repositorio completo con cobertura de Angular, backend, SQL, Docker, CI y configuración.
- [ ] Modelar activos: JWT, credenciales, información fiscal, inventario, pagos, Sync y acceso por sucursal.
- [ ] Enumerar fronteras de confianza y actores: anónimo, usuario autenticado, cajero, almacén, administrador y dispositivo.
- [ ] Definir casos OWASP ASVS/API Top 10 aplicables y excluir pruebas destructivas externas.

**Aceptación:** cada prueba posterior se vincula a un activo, frontera, amenaza y resultado esperado.

### Tarea 9: Ejecutar Análisis Estático Y De Cadena De Suministro

- [ ] Ejecutar Gitleaks sobre historial completo.
- [ ] Ejecutar Trivy sobre filesystem, configuración y la imagen backend construida.
- [ ] Ejecutar `npm audit` y revisar dependencias runtime y desarrollo por separado.
- [ ] Revisar el grafo Gradle y CVE de dependencias transitivas.
- [ ] Revisar bundles Angular para secretos, URLs privadas y source maps no intencionales.
- [ ] Revisar Dockerfiles, Compose, workflows, permisos de GitHub y acciones fijadas por SHA.
- [ ] Validar manualmente cada candidato antes de declararlo hallazgo.

**Aceptación:** cada hallazgo conserva archivo/línea, causa raíz, impacto, severidad, confianza y evidencia; los falsos positivos quedan descartados explícitamente.

### Tarea 10: Ejecutar DAST Y Casos Dirigidos

**Archivos:** `tests/security/zap/` y `tests/security/cases/`.

- [ ] Ejecutar ZAP baseline sobre Angular y OpenAPI sin autenticación.
- [ ] Ejecutar ZAP autenticado con un usuario de privilegios mínimos contra la base efímera.
- [ ] Probar manualmente `401`, `403`, acceso horizontal entre sucursales e IDOR con UUID ajenos.
- [ ] Probar JWT alterado, expirado, revocado y con `auth_version` anterior.
- [ ] Probar SQL/JSON injection, XSS reflejado/almacenado, mass assignment, CORS, headers, payloads grandes y profundidad Sync.
- [ ] Probar rate limiting por IP/cuenta y caída controlada de Redis en el perfil distribuido.
- [ ] Probar concurrencia ofensiva sobre ventas, pagos, devoluciones, cierre de caja, recepciones e idempotencia.
- [ ] Confirmar que no se registran tokens, contraseñas ni payloads fiscales completos en logs.

**Aceptación:** las pruebas no escapan de localhost, no usan datos reales y toda vulnerabilidad reproducible cuenta con solicitud mínima, respuesta, correlation id e impacto demostrado de forma segura.

### Tarea 11: Consolidar Hallazgos De Seguridad

**Archivo:** el mismo informe `docs/reports/quality/2026-08-30-fullstack-stress-security-analysis.md`.

- [ ] Añadir resumen ejecutivo con conteo por severidad y confianza.
- [ ] Documentar cobertura real y zonas no verificadas.
- [ ] Para cada hallazgo incluir ID, CWE/OWASP, superficie, precondición, pasos de reproducción sanitizados, impacto, evidencia y recomendación.
- [ ] Relacionar vulnerabilidades con comportamientos observados bajo estrés cuando aplique.
- [ ] Ordenar remediación por criticidad, explotabilidad y costo.
- [ ] No modificar código productivo durante esta tarea.
- [ ] Presentar el informe al usuario y acordar qué hallazgos se corregirán primero.

**Aceptación:** no se reportan sospechas como vulnerabilidades confirmadas y el documento permite crear una tarea de corrección independiente por hallazgo.

### Tarea 12: Preparar La Fase De Corrección

- [ ] Crear un plan separado de remediación únicamente para hallazgos validados y aprobados.
- [ ] Escribir primero una prueba de regresión por vulnerabilidad.
- [ ] Corregir en commits pequeños sin mezclar hallazgos.
- [ ] Repetir el caso ofensivo, la suite del módulo y el escaneo de verificación.
- [ ] Repetir Load si la corrección toca consultas, filtros, autenticación o transacciones.

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
docker compose --project-name tienda-perf --env-file .performance\perf.env down -v --remove-orphans
```

Los comandos específicos de k6, Lighthouse y ZAP se incorporarán a los scripts versionados; el operador no tendrá que reconstruir parámetros manualmente.

## 10. Criterio De Cierre

La campaña se considera terminada únicamente cuando:

- [ ] el entorno aislado puede crearse y destruirse de forma repetible;
- [ ] los cinco perfiles de rendimiento tienen evidencia;
- [ ] Angular fue medido con build de producción y navegador real;
- [ ] se verificó integridad después de carga y concurrencia;
- [ ] el escaneo estático y DAST cubrieron las superficies autorizadas;
- [ ] cada hallazgo está validado o descartado;
- [ ] el informe Markdown consolidado está completo y sanitizado;
- [ ] el usuario revisó el informe antes de iniciar correcciones;
- [ ] no quedaron servicios, volúmenes, credenciales ni artefactos sensibles activos.

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
