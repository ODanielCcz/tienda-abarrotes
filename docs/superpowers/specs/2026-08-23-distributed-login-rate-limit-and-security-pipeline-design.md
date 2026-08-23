# Diseño De Rate Limiting Distribuido Y Pipeline De Seguridad

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | ARQ-SEG-002 |
| Versión | v0.1 |
| Estado | Aprobado |
| Responsable | ODCC |
| Fecha | 2026-08-23 |
| Clasificación | Público |

## Control De Versiones

| Versión | Fecha | Autor | Cambio | Estado |
|---|---|---|---|---|
| v0.1 | 2026-08-23 | ODCC / Codex | Diseño aprobado de rate limiting distribuido con Redis y controles automatizados de seguridad | Aprobado |

## Propósito

Definir cómo sustituir el rate limiting local del inicio de sesión por un control distribuido respaldado por Redis y cómo completar el pipeline de seguridad del repositorio público. El diseño conserva la arquitectura hexagonal, evita almacenar identificadores personales legibles en Redis y establece un comportamiento fail-closed para nuevos inicios de sesión cuando Redis no esté disponible en producción.

## Contexto Verificado

La especificación parte de la revisión `daaa807` de la rama `feature/backend-security-hardening`.

| Evidencia | Estado Observado | Consecuencia De Diseño |
|---|---|---|
| `LoginService` depende de `LoginRateLimitPort` | Verificado | Redis se implementará como adaptador de salida sin contaminar dominio ni aplicación. |
| `InMemoryLoginRateLimiter` | Implementado | Se conservará exclusivamente para perfiles `local` y `test`. |
| Repositorio GitHub | Público, rama predeterminada `main` | Dependency Review, Secret Scanning y Push Protection pueden habilitarse. |
| Dependabot | Configurado y generando ramas | Se conserva; no se añade un segundo actualizador. |
| Trivy | Escaneo de imagen con bloqueo para vulnerabilidades `HIGH` y `CRITICAL` | Se conserva y se complementa con controles previos al build. |
| Gitleaks 8.30.1 | 46 commits revisados; dos coincidencias sintéticas de prueba | Se permitirán únicamente por fingerprint exacto. |
| Flyway en CI | El workflow espera `V034`, mientras la rama llega a `V036` | Se eliminará el número fijo y se calculará la última migración esperada. |

## Decisión Arquitectónica

Se implementará un adaptador `RedisLoginRateLimiter` sobre el puerto existente `LoginRateLimitPort`. La selección del adaptador será explícita mediante configuración y perfiles:

| Entorno | Proveedor Predeterminado | Comportamiento Sin Redis |
|---|---|---|
| `local` | `memory` | No requiere Redis. |
| `test` | `memory` | Las pruebas generales permanecen deterministas. |
| Integración Redis | `redis` | Testcontainers suministra una instancia real. |
| `prod` | `redis` | Nuevos logins responden `503`; no existe fallback silencioso. |

El puerto de aplicación permanecerá estable. Las clases de aplicación no importarán Spring Data Redis, Lettuce, scripts Lua ni objetos de infraestructura.

## Arquitectura Objetivo

```text
Cliente
  │
  ▼
AuthenticationController
  │ LoginCommand(clientAddress, username, password)
  ▼
LoginService
  │
  ▼
LoginRateLimitPort
  ├── InMemoryLoginRateLimiter   [local, test]
  └── RedisLoginRateLimiter      [prod, integración Redis]
          │
          ├── RateLimitKeyEncoder (HMAC-SHA-256)
          ├── check-rate-limit.lua
          └── record-login-failure.lua
                    │
                    ▼
                  Redis
```

## Componentes

| Componente | Capa | Responsabilidad |
|---|---|---|
| `LoginRateLimitPort` | Aplicación | Contrato estable para comprobar, registrar fallo y limpiar contadores. |
| `RedisLoginRateLimiter` | Adaptador de salida | Ejecutar decisiones e incrementos distribuidos. |
| `InMemoryLoginRateLimiter` | Adaptador de salida | Soportar desarrollo local y pruebas sin infraestructura externa. |
| `LoginRateLimitProperties` | Configuración | Validar proveedor, ventana, límites, namespace y secreto de claves. |
| `RateLimitKeyEncoder` | Infraestructura | Derivar claves opacas mediante HMAC-SHA-256. |
| Scripts Lua | Infraestructura | Mantener incrementos y expiraciones atómicos. |
| `LoginRateLimitUnavailableException` | Aplicación | Representar indisponibilidad técnica sin filtrar excepciones de Redis. |
| `AuthenticationExceptionHandler` | Entrada REST | Mapear límite a `429` e indisponibilidad a `503`. |

## Algoritmo De Rate Limiting

### Dimensiones

| Dimensión | Límite Predeterminado | Ventana | Limpieza En Login Correcto |
|---|---:|---:|---|
| IP | 20 fallos | 1 minuto | No |
| IP + cuenta | 5 fallos | 1 minuto | Sí |
| Cuenta | 10 fallos | 1 minuto | Sí |

Los valores serán configurables, pero nunca podrán ser menores que uno. La ventana utilizará `Duration` y tendrá un valor predeterminado de `PT1M`.

### Claves

Redis no almacenará IP, username ni combinaciones en texto claro. Las claves serán:

```text
tienda:auth:rate-limit:v1:ip:{hmac-hex}
tienda:auth:rate-limit:v1:pair:{hmac-hex}
tienda:auth:rate-limit:v1:account:{hmac-hex}
```

El HMAC utilizará `RATE_LIMIT_KEY_SECRET_BASE64`, separado del secreto JWT. Rotar este secreto reinicia efectivamente las ventanas activas, lo cual es aceptable porque su duración máxima será de un minuto.

### Operaciones Atómicas

`check-rate-limit.lua` leerá las tres dimensiones y devolverá si la solicitud está permitida y el mayor tiempo restante de bloqueo. `record-login-failure.lua` incrementará las tres dimensiones y asignará TTL al crear cada contador. La operación completa se ejecutará en Redis de forma atómica.

No se utilizará una secuencia separada `INCR` seguida de `EXPIRE`, porque un fallo entre ambas operaciones podría dejar una clave sin caducidad. Los scripts se declararán como beans únicos para aprovechar el caché SHA de Spring Data Redis.

### Login Correcto

Un login correcto eliminará los contadores de cuenta y de combinación IP/cuenta. El contador por IP no se eliminará, evitando que credenciales válidas permitan limpiar abuso previo desde la misma dirección.

## Manejo De Fallos

| Condición | HTTP | Código De Negocio | `Retry-After` | Resultado |
|---|---:|---|---:|---|
| Dentro del límite | 200 o error de autenticación correspondiente | Existente | No | Continúa el flujo. |
| Límite agotado | 429 | `LOGIN_RATE_LIMITED` | TTL restante | No verifica credenciales. |
| Redis no disponible en producción | 503 | `LOGIN_RATE_LIMIT_UNAVAILABLE` | 5 segundos | Bloquea únicamente nuevos logins. |
| Configuración Redis inválida | Fallo de arranque | No aplica | No | La instancia no se declara lista. |

Los JWT ya emitidos no consultarán Redis y continuarán funcionando. La indisponibilidad se traducirá en el adaptador; aplicación y REST no recibirán `RedisConnectionFailureException` ni `DataAccessException`.

## Configuración

| Variable | Propiedad | Obligatoria En `prod` | Valor Predeterminado Fuera De `prod` |
|---|---|---:|---|
| `LOGIN_RATE_LIMIT_PROVIDER` | `app.security.login-rate-limit.provider` | Sí, `redis` | `memory` |
| `LOGIN_RATE_LIMIT_WINDOW` | `app.security.login-rate-limit.window` | No | `PT1M` |
| `LOGIN_RATE_LIMIT_IP_MAX_FAILURES` | `app.security.login-rate-limit.ip-max-failures` | No | `20` |
| `LOGIN_RATE_LIMIT_PAIR_MAX_FAILURES` | `app.security.login-rate-limit.pair-max-failures` | No | `5` |
| `LOGIN_RATE_LIMIT_ACCOUNT_MAX_FAILURES` | `app.security.login-rate-limit.account-max-failures` | No | `10` |
| `RATE_LIMIT_KEY_SECRET_BASE64` | `app.security.login-rate-limit.key-secret-base64` | Sí | Sin valor |
| `REDIS_HOST` | `spring.data.redis.host` | Sí | `localhost` |
| `REDIS_PORT` | `spring.data.redis.port` | No | `6379` |
| `REDIS_PASSWORD` | `spring.data.redis.password` | Sí | Sin valor |
| `REDIS_CONNECT_TIMEOUT` | `spring.data.redis.connect-timeout` | No | `PT2S` |
| `REDIS_TIMEOUT` | `spring.data.redis.timeout` | No | `PT1S` |

`.env.example` incluirá únicamente placeholders. Los valores reales permanecerán en `.env` ignorado o en el gestor de secretos del ambiente.

## Docker Y Operación

La infraestructura local de validación utilizará la imagen oficial `redis:8.8.1-alpine` y no utilizará `latest`.

- Redis permanecerá en la red interna `tienda_data`.
- No publicará el puerto `6379` al host de forma predeterminada.
- Exigirá contraseña y expondrá un healthcheck autenticado.
- No tendrá volumen persistente, AOF ni snapshots; perder contadores durante un reinicio es aceptable.
- El perfil Compose `distributed-security` habilitará Redis para pruebas manuales.
- Producción deberá usar una instancia privada o un servicio administrado con TLS, autenticación y alta disponibilidad.
- Redis formará parte de readiness, no de liveness, para evitar reinicios en cascada del backend.

El backend no quedará expuesto directamente cuando exista un proxy inverso. El proxy deberá sobrescribir encabezados de forwarding; no se confiará en un `X-Forwarded-For` proporcionado directamente por Internet.

## Observabilidad

Se publicarán métricas Micrometer sin etiquetas de alta cardinalidad:

| Métrica Micrometer | Prometheus | Etiquetas Permitidas |
|---|---|---|
| `auth.rate.limit.allowed` | `auth_rate_limit_allowed_total` | `provider` |
| `auth.rate.limit.blocked` | `auth_rate_limit_blocked_total` | `provider`, `dimension` |
| `auth.rate.limit.backend.errors` | `auth_rate_limit_backend_errors_total` | `provider`, `operation` |
| `auth.rate.limit.redis.latency` | `auth_rate_limit_redis_latency_seconds` | `operation` |

No se usarán IP, username, hashes completos ni claves Redis como etiquetas. Los logs incluirán `correlationId`, operación y resultado, sin valores sensibles.

## Pipeline De Seguridad

### Controles Existentes Que Se Conservan

- Dependabot semanal para Gradle, GitHub Actions y Docker.
- Suite completa Java 21 con Gradle Wrapper.
- Migraciones Flyway y smoke test de la imagen.
- Trivy sobre la imagen final, bloqueando vulnerabilidades `HIGH` y `CRITICAL` con corrección disponible.

### Controles Nuevos

| Control | Momento | Política |
|---|---|---|
| Gitleaks | Pull Request, `main`, ejecución semanal | Historial completo, `fetch-depth: 0`, salida redactada y bloqueo ante secretos no permitidos. |
| Dependency Submission para Gradle | Pull Request y `main` | Publicar el grafo real de dependencias del backend. |
| Dependency Review | Pull Request | Bloquear nuevas dependencias vulnerables con severidad `HIGH` o `CRITICAL`. |
| Trivy filesystem/misconfiguration | Pull Request y semanal | Revisar Dockerfiles, Compose y configuración antes de construir la imagen. |
| GitHub Secret Scanning | Repositorio | Habilitado en Settings. |
| GitHub Push Protection | Repositorio | Habilitado; todo bypass deberá justificar motivo. |

Las acciones de terceros se fijarán por SHA completo. Cada referencia incluirá un comentario con su versión legible, igual que el workflow actual.

### Falsos Positivos Verificados

Gitleaks encontró dos coincidencias sintéticas y no reutilizadas en el `.env` local:

| Regla | Archivo | Commit | Línea | Tratamiento |
|---|---|---|---:|---|
| `generic-api-key` | `ApiResponseDtoTest.java` | `a25e1e031524bee789300f9e02b69b78e084c7e1` | 25 | Fingerprint exacto en `.gitleaksignore`. |
| `generic-api-key` | `application-test.yml` | `ecada0dd1051d495a0743b278791628dfd98d35f` | 25 | Fingerprint exacto en `.gitleaksignore`. |

No se permitirán exclusiones generales de `src/test`, YAML o cadenas base64.

### Validación Flyway

El workflow dejará de comparar contra la constante `034`. Calculará la versión más alta a partir de `database/migrations/V*__*.sql` y la comparará con `flyway_schema_history`. Esto impedirá que una migración nueva requiera actualizar manualmente el workflow.

## Estrategia De Pruebas

### Unitarias

- Propiedades inválidas rechazan el arranque del adaptador Redis.
- `RateLimitKeyEncoder` produce claves deterministas y no contiene valores de entrada.
- Las excepciones técnicas se traducen a `LoginRateLimitUnavailableException`.
- `AuthenticationExceptionHandler` responde `429` y `503` con `ApiResponseDto`, `reason` y `correlationId`.

### Integración Con Redis

- Dos instancias de `RedisLoginRateLimiter` comparten contadores.
- Fallos concurrentes no pierden incrementos.
- Cada dimensión bloquea en su umbral correspondiente.
- El TTL expira y permite nuevos intentos.
- Un login correcto elimina cuenta y par, pero conserva IP.
- Redis detenido produce `503` y nunca cambia silenciosamente a memoria.
- Las claves observadas no contienen IP ni username legibles.

Las pruebas usarán Testcontainers con la misma versión mayor de Redis definida para Docker. Las pruebas generales conservarán el adaptador en memoria y no dependerán de Redis.

### Pipeline

- Gitleaks falla ante un secreto sintético introducido en una rama de prueba controlada.
- Los dos fingerprints aprobados no fallan.
- Dependency Review bloquea una dependencia vulnerable agregada en un PR de validación controlado.
- Trivy continúa bloqueando una imagen con vulnerabilidad `HIGH` o `CRITICAL` corregible.
- La validación de Flyway detecta automáticamente la última migración.

## Despliegue

1. Integrar primero `feature/backend-security-hardening` en `main`.
2. Incorporar Gitleaks y los controles de dependencias; resolver hallazgos antes de cambiar runtime.
3. Agregar dependencias, propiedades, scripts y pruebas Redis manteniendo `memory` como proveedor fuera de producción.
4. Levantar Redis en un ambiente de integración y ejecutar pruebas de dos instancias.
5. Desplegar Redis y validar healthcheck, métricas y alertas.
6. Activar `provider=redis` en una instancia canary del backend.
7. Confirmar `429`, expiración, latencia y respuesta `503` durante una interrupción controlada.
8. Habilitar todas las instancias y observar errores/latencia durante una ventana operativa.

## Rollback

- El rollback normal restaura la versión anterior del backend y mantiene Redis sin consumidores hasta retirarlo.
- Cambiar producción a `provider=memory` solo se permitirá como mitigación temporal documentada cuando exista una única instancia; reduce la garantía de seguridad distribuida.
- No hay rollback de base de datos porque el diseño no crea tablas ni migraciones.
- Deshabilitar un check de CI requiere un commit revisado; no se usará `continue-on-error` para ocultar fallos.

## Riesgos

| Código | Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|---|
| RIE-001 | Redis bloquea nuevos logins durante una caída | Media | Alto | Alta disponibilidad, timeouts cortos, readiness y alertas; JWT existentes continúan. |
| RIE-002 | Redis agrega latencia al login | Baja | Medio | Scripts cacheados, red privada, timeout de un segundo y métrica de latencia. |
| RIE-003 | Un proxy permite suplantar IP | Media | Alto | Backend no expuesto directamente y forwarding sobrescrito por proxy confiable. |
| RIE-004 | Allowlist de Gitleaks oculta un secreto futuro | Baja | Alto | Allowlist únicamente por fingerprint exacto; sin exclusiones por ruta. |
| RIE-005 | Dependency Review no recibe un grafo Gradle completo | Media | Medio | Dependency Submission explícito y verificación del snapshot en CI. |
| RIE-006 | Rotar el secreto HMAC reinicia límites activos | Baja | Bajo | Ventanas de un minuto y rotación durante baja actividad. |

## No Incluido

- Sesiones HTTP o JWT almacenados en Redis.
- Caché de catálogo, precios, inventario o reportes.
- Rate limiting general de todos los endpoints.
- KrakenD, Envoy, NGINX o Cloudflare como gateway.
- Redis Cluster para el entorno local.
- Persistencia de contadores.

## Criterios De Aceptación

| Código | Criterio | Evidencia |
|---|---|---|
| CA-001 | Dos instancias comparten el mismo límite | Prueba de integración Redis. |
| CA-002 | Fallos concurrentes respetan umbrales y TTL | Prueba concurrente con Testcontainers. |
| CA-003 | Producción no usa fallback a memoria | Prueba de configuración y caída Redis. |
| CA-004 | Redis no almacena identificadores legibles | Inspección automatizada de claves. |
| CA-005 | `429` y `503` mantienen el contrato API | Pruebas REST. |
| CA-006 | Local y tests funcionan sin Redis | Suite existente completa. |
| CA-007 | Gitleaks revisa todo el historial sin falsos positivos amplios | Workflow y `.gitleaksignore`. |
| CA-008 | Nuevas dependencias vulnerables se bloquean en PR | Dependency Review. |
| CA-009 | Trivy, Dependabot y Flyway siguen operativos | Ejecución completa de CI. |
| CA-010 | No se versionan secretos nuevos | Push Protection, Gitleaks y revisión de `.env.example`. |

## Referencias

- [Spring Data Redis: scripting](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html)
- [Redis: rate limiting](https://redis.io/docs/latest/develop/use-cases/rate-limiter/)
- [Imagen oficial Redis en Docker Hub](https://hub.docker.com/_/redis)
- [GitHub: Dependency Review](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/reviewing-dependency-changes-in-a-pull-request)
- [Gradle Dependency Submission](https://github.com/gradle/actions/blob/main/docs/dependency-submission.md)
- [GitHub: Push Protection](https://docs.github.com/en/code-security/concepts/secret-security/push-protection)
- [Gitleaks](https://github.com/gitleaks/gitleaks)

## Pendientes De Ejecución

La implementación comenzará únicamente después de convertir esta especificación aprobada en un plan TDD con tareas, archivos, comandos, verificaciones y commits concretos.
