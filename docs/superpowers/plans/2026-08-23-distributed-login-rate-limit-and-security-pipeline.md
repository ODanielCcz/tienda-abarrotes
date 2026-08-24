# Rate Limiting Distribuido Y Pipeline De Seguridad — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir el rate limiting local del login por un proveedor Redis distribuido en producción y completar los controles automatizados de secretos, dependencias y configuración del repositorio público.

**Architecture:** `LoginService` conservará su dependencia sobre `LoginRateLimitPort`; la selección entre memoria y Redis ocurrirá exclusivamente en configuración Spring. El adaptador Redis usará claves HMAC opacas y scripts Lua atómicos, fallará cerrado con `503` si Redis no está disponible y expondrá métricas de baja cardinalidad. Los workflows de GitHub se separarán por responsabilidad y todas las acciones externas quedarán fijadas por SHA.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Data Redis/Lettuce, Micrometer, Redis 8.8.1, Lua, Testcontainers, Gradle Kotlin DSL, Docker Compose, GitHub Actions, Gitleaks 3.0.0, Trivy 0.36.0.

**Spec:** `docs/superpowers/specs/2026-08-23-distributed-login-rate-limit-and-security-pipeline-design.md`

## Global Constraints

- Integrar primero `feature/backend-security-hardening` en `main` o ejecutar este plan desde una rama basada en el commit `21f7102`.
- No crear migraciones PostgreSQL ni almacenar sesiones o JWT en Redis.
- `LoginRateLimitPort` permanece en la capa de aplicación; Spring Data Redis, Lettuce, Micrometer y Lua permanecen en adaptadores/configuración.
- Proveedor predeterminado fuera de producción: `memory`; proveedor obligatorio en `prod`: `redis`.
- Límites predeterminados: IP `20`, IP+cuenta `5`, cuenta `10`, todos dentro de `PT1M` y nunca menores que uno.
- Redis no puede almacenar IP, username ni combinaciones en texto claro; las claves usan HMAC-SHA-256 con `RATE_LIMIT_KEY_SECRET_BASE64`, distinto del secreto JWT.
- Redis caído en producción devuelve `503 LOGIN_RATE_LIMIT_UNAVAILABLE` y `Retry-After: 5`; no existe fallback silencioso a memoria.
- Redis usa `redis:8.8.1-alpine`, red interna, contraseña, healthcheck autenticado, sin puerto publicado y sin persistencia.
- Redis participa en readiness, no en liveness.
- Las métricas no pueden contener IP, username, hashes completos ni claves Redis como tags.
- Gitleaks debe revisar historial completo y permitir únicamente los dos fingerprints exactos aprobados.
- Todas las acciones externas de GitHub Actions deben fijarse por SHA completo con comentario de versión.
- No ejecutar `Flyway clean`, no borrar volúmenes locales y no versionar valores reales de secretos.
- Antes de cada grupo de cambios ejecutar `codegraph explore`; si el índice vuelve a fallar, registrar el error y usar `rg` como respaldo.
- Crear una rama funcional sin la palabra `codex`, por ejemplo `security/distributed-login-rate-limit`.

---

## Mapa De Archivos

### Pipeline

- Crear `.gitleaksignore`: allowlist por fingerprint exacto.
- Crear `.github/workflows/security-scans.yml`: Gitleaks y Trivy filesystem/misconfiguration.
- Crear `.github/workflows/dependency-graph.yml`: generar/subir el grafo Gradle en PR y enviarlo en `main`.
- Crear `.github/workflows/dependency-graph-submit.yml`: enviar de forma segura el artefacto generado por un PR.
- Crear `.github/workflows/dependency-review.yml`: bloquear dependencias nuevas `HIGH`/`CRITICAL`.
- Modificar `.github/workflows/backend-ci.yml`: calcular dinámicamente la última versión Flyway.
- Modificar `.github/dependabot.yml`: vigilar también la imagen Redis en el Compose raíz.

### Runtime

- Modificar `apps/backend/build.gradle.kts`: agregar Spring Data Redis.
- Crear `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/config/LoginRateLimitProperties.java`: propiedades validadas.
- Crear `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/config/LoginRateLimitConfiguration.java`: selección explícita del adaptador y scripts singleton.
- Crear `apps/backend/src/main/java/com/odcc/tienda/modules/identity/application/exception/LoginRateLimitUnavailableException.java`: error técnico independiente de Redis.
- Crear `apps/backend/src/main/java/com/odcc/tienda/modules/identity/application/model/LoginRateLimitDimension.java`: dimensión bloqueada estable.
- Crear `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/RateLimitKeyEncoder.java`: claves HMAC opacas.
- Crear `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/LoginRateLimitMetrics.java`: métricas Micrometer.
- Crear `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/RedisLoginRateLimiter.java`: adaptador distribuido.
- Modificar `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/InMemoryLoginRateLimiter.java`: quitar autodetección y usar propiedades/métricas compartidas.
- Crear `apps/backend/src/main/resources/redis/check-rate-limit.lua`: decisión y reserva atómica de capacidad.
- Crear `apps/backend/src/main/resources/redis/clear-successful-login-reservation.lua`: liberar la reserva de la solicitud exitosa sin borrar fallos IP anteriores.
- Modificar `apps/backend/src/main/java/com/odcc/tienda/modules/identity/application/exception/LoginRateLimitedException.java`: incluir dimensión.
- Modificar `apps/backend/src/main/java/com/odcc/tienda/modules/identity/application/usecase/LoginService.java`: limpiar Redis antes de emitir el JWT.
- Modificar `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/in/rest/error/AuthenticationExceptionHandler.java`: respuesta `503` y `correlationId`.

### Configuración, operación y pruebas

- Modificar `apps/backend/src/main/resources/application.yml`, `application-local.yml`, `application-prod.yml`.
- Crear `apps/backend/src/main/resources/application-redis.yml`: perfil explícito para validación distribuida local.
- Modificar `apps/backend/src/test/resources/application.properties`: memoria y límites altos para la suite general.
- Modificar `compose.yaml` y `.env.example`: servicio Redis y variables sin secretos reales.
- Crear pruebas unitarias y de integración bajo los paquetes existentes de `identity`.
- Crear `docs/decisions/ADR-0008-rate-limiting-distribuido.md`.
- Crear `docs/operations/distributed-login-rate-limit.md`.
- Modificar `docs/operations/ci-cd.md`, `README.md` y `PROJECT.md`.

---

### Task 1: Completar Los Controles De Seguridad En GitHub Actions

**Files:**
- Create: `.gitleaksignore`
- Create: `.github/workflows/security-scans.yml`
- Create: `.github/workflows/dependency-graph.yml`
- Create: `.github/workflows/dependency-graph-submit.yml`
- Create: `.github/workflows/dependency-review.yml`
- Modify: `.github/workflows/backend-ci.yml:65-75`
- Modify: `.github/dependabot.yml:21-25`

**Interfaces:**
- Consumes: Gradle Wrapper en `apps/backend/gradlew`, migraciones `database/migrations/V*__*.sql` y permisos GitHub de un repositorio público.
- Produces: checks obligatorios `Secret scan`, `Filesystem security scan`, `Dependency graph`, `Dependency review` y validación Flyway sin versión fija.

- [ ] **Step 1: Crear la allowlist mínima de Gitleaks**

Agregar únicamente estas dos líneas a `.gitleaksignore`:

```text
a25e1e031524bee789300f9e02b69b78e084c7e1:apps/backend/src/test/java/com/odcc/tienda/shared/web/response/ApiResponseDtoTest.java:generic-api-key:25
ecada0dd1051d495a0743b278791628dfd98d35f:apps/backend/src/main/resources/application-test.yml:generic-api-key:25
```

No agregar exclusiones por carpeta, extensión, regex o regla completa.

- [ ] **Step 2: Escribir el workflow de secretos y configuración**

Crear `.github/workflows/security-scans.yml` con `pull_request`, `push` a `main`, ejecución semanal y manual. Usar:

```yaml
permissions:
  contents: read

jobs:
  secrets:
    name: Secret scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6
        with:
          fetch-depth: 0
      - uses: gitleaks/gitleaks-action@e0c47f4f8be36e29cdc102c57e68cb5cbf0e8d1e # v3.0.0
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  filesystem:
    name: Filesystem security scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6
      - uses: aquasecurity/trivy-action@a9c7b0f06e461e9d4b4d1711f154ee024b8d7ab8 # v0.36.0
        with:
          scan-type: fs
          scan-ref: .
          scanners: vuln,misconfig
          severity: HIGH,CRITICAL
          ignore-unfixed: true
          exit-code: '1'
          format: table
```

- [ ] **Step 3: Crear la generación y envío del grafo Gradle**

En `.github/workflows/dependency-graph.yml`, usar Java 21 y el SHA `16f3e46a58d2b926c34615132d7969a96bccb22b` (`gradle/actions/dependency-submission` v6.0.0). Definir dos jobs:

```yaml
permissions:
  contents: read

jobs:
  pull-request-graph:
    if: github.event_name == 'pull_request'
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6
      - uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/dependency-submission@16f3e46a58d2b926c34615132d7969a96bccb22b # v6.0.0
        with:
          build-root-directory: apps/backend
          dependency-graph: generate-and-upload
          artifact-retention-days: 1

  main-graph:
    if: github.event_name == 'push' || github.event_name == 'workflow_dispatch'
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6
      - uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/dependency-submission@16f3e46a58d2b926c34615132d7969a96bccb22b # v6.0.0
        with:
          build-root-directory: apps/backend
          dependency-graph: generate-and-submit
```

El workflow debe activarse en `pull_request`, `push` a `main` y `workflow_dispatch`.

- [ ] **Step 4: Enviar de forma segura el artefacto de PR y revisar dependencias**

Crear `.github/workflows/dependency-graph-submit.yml` con `workflow_run` sobre `Dependency Graph`, tipos `completed`, `actions: read` y `contents: write`. Ejecutar solamente cuando el workflow original termine `success` y su evento sea `pull_request`; usar la misma acción v6.0.0 con:

```yaml
with:
  dependency-graph: download-and-submit
```

Crear `.github/workflows/dependency-review.yml` para `pull_request` con:

```yaml
permissions:
  contents: read

steps:
  - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6
  - uses: actions/dependency-review-action@05fe4576374b728f0c523d6a13d64c25081e0803 # v4.8.3
    with:
      fail-on-severity: high
      retry-on-snapshot-warnings: true
      retry-on-snapshot-warnings-timeout: 600
```

- [ ] **Step 5: Reemplazar la versión Flyway fija**

En `backend-ci.yml`, sustituir `test "$version" = '034'` por:

```bash
expected=$(find database/migrations -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' \
  | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -1)
actual=$(docker compose --env-file "$COMPOSE_ENV_FILE" exec -T database \
  psql -U tienda_owner -d tienda_ci -Atc \
  "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;")
test "$((10#$actual))" -eq "$((10#$expected))"
```

Agregar a Dependabot:

```yaml
  - package-ecosystem: docker
    directory: /
    schedule:
      interval: weekly
    open-pull-requests-limit: 5
```

- [ ] **Step 6: Validar sintaxis y comportamiento local**

Ejecutar:

```powershell
git diff --check
docker compose config --quiet
gitleaks git --redact --log-opts="--all"
```

Expected: los YAML y Compose son válidos; Gitleaks finaliza sin hallazgos no permitidos; la allowlist contiene exactamente dos líneas.

- [ ] **Step 7: Commit**

```powershell
git add .gitleaksignore .github/workflows .github/dependabot.yml
git commit -m "ci(security): add secret and dependency controls"
```

---

### Task 2: Definir Propiedades Validadas Y Selección De Proveedor

**Files:**
- Modify: `apps/backend/build.gradle.kts:30-60`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/config/LoginRateLimitProperties.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/config/LoginRateLimitConfiguration.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/InMemoryLoginRateLimiter.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/config/LoginRateLimitConfigurationTest.java`
- Modify: `apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/out/security/InMemoryLoginRateLimiterTest.java`

**Interfaces:**
- Consumes: `Clock`, `LoginRateLimitPort`, `MeterRegistry`, `StringRedisTemplate`.
- Produces: `LoginRateLimitProperties`, enum `Provider { MEMORY, REDIS }` y exactamente un bean `LoginRateLimitPort`.

- [ ] **Step 1: Escribir pruebas fallidas de binding y selección**

Crear `LoginRateLimitConfigurationTest` con `ApplicationContextRunner`. Cubrir estos métodos:

```java
@Test void shouldUseMemoryProviderByDefault()
@Test void shouldUseRedisProviderWhenConfigured()
@Test void shouldRejectRedisWithoutAValidBase64Secret()
@Test void shouldRejectLimitsBelowOne()
@Test void shouldRejectMemoryProviderWhenProdProfileIsActive()
```

Cada prueba debe verificar `context.getBeansOfType(LoginRateLimitPort.class).size() == 1`; la última debe comprobar `context.getStartupFailure()` y que su cadena causal contenga `prod requiere el proveedor redis`.

- [ ] **Step 2: Ejecutar y confirmar el fallo**

```powershell
Set-Location apps/backend
.\gradlew.bat test --tests "*LoginRateLimitConfigurationTest" --no-daemon
```

Expected: FAIL porque las propiedades y configuración todavía no existen.

- [ ] **Step 3: Agregar Redis al build y crear propiedades exactas**

Agregar:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

Crear un record `LoginRateLimitProperties` con:

```java
@ConfigurationProperties("app.security.login-rate-limit")
@Validated
public record LoginRateLimitProperties(
    @NotNull Provider provider,
    @NotNull @DurationMin(seconds = 1) Duration window,
    @Min(1) int ipMaxFailures,
    @Min(1) int pairMaxFailures,
    @Min(1) int accountMaxFailures,
    @NotBlank String namespace,
    String keySecretBase64,
    @NotNull @DurationMin(seconds = 1) Duration unavailableRetryAfter
) {
    public enum Provider { MEMORY, REDIS }

    @AssertTrue(message = "Redis requiere un secreto Base64 de al menos 32 bytes")
    public boolean isRedisSecretValid() {
        if (provider != Provider.REDIS) return true;
        try {
            return keySecretBase64 != null
                && Base64.getDecoder().decode(keySecretBase64).length >= 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Crear configuración condicional sin autodetección accidental**

Quitar `@Component` y `@Value` de `InMemoryLoginRateLimiter`. Hacer que su constructor consuma `Clock`, `LoginRateLimitProperties` y `LoginRateLimitMetrics`.

En `LoginRateLimitConfiguration`, declarar:

```java
@Bean
@ConditionalOnProperty(
    name = "app.security.login-rate-limit.provider",
    havingValue = "memory",
    matchIfMissing = true
)
LoginRateLimitPort inMemoryLoginRateLimiter(
    Clock clock,
    LoginRateLimitProperties properties,
    LoginRateLimitMetrics metrics
) {
    return new InMemoryLoginRateLimiter(clock, properties, metrics);
}

@Bean
@ConditionalOnProperty(
    name = "app.security.login-rate-limit.provider",
    havingValue = "redis"
)
LoginRateLimitPort redisLoginRateLimiter(
    StringRedisTemplate redis,
    RedisScript<String> checkLoginRateLimitScript,
    RedisScript<Long> recordLoginFailureScript,
    RateLimitKeyEncoder keyEncoder,
    LoginRateLimitProperties properties,
    LoginRateLimitMetrics metrics
) {
    return new RedisLoginRateLimiter(
        redis,
        checkLoginRateLimitScript,
        recordLoginFailureScript,
        keyEncoder,
        properties,
        metrics
    );
}
```

Agregar un `SmartInitializingSingleton` activo bajo `@Profile("prod")` que lance `IllegalStateException("El perfil prod requiere el proveedor redis")` cuando `properties.provider() != REDIS`. No capturar esa excepción.

- [ ] **Step 5: Mantener las reglas de memoria y probarlas**

Ampliar `InMemoryLoginRateLimiterTest` para verificar IP, par, cuenta, expiración con `Clock` mutable y limpieza exitosa de par/cuenta sin limpiar IP. Usar `SimpleMeterRegistry` y propiedades con los límites exactos de cada caso.

- [ ] **Step 6: Ejecutar pruebas**

```powershell
.\gradlew.bat test --tests "*LoginRateLimitConfigurationTest" --tests "*InMemoryLoginRateLimiterTest" --no-daemon
```

Expected: PASS; cada contexto contiene un solo proveedor y `prod+memory` falla al arrancar.

- [ ] **Step 7: Commit**

```powershell
git add apps/backend/build.gradle.kts apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter
git commit -m "feat(identity): configure selectable login rate limit providers"
```

---

### Task 3: Generar Claves HMAC Opacas

**Files:**
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/RateLimitKeyEncoder.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/out/security/RateLimitKeyEncoderTest.java`

**Interfaces:**
- Consumes: namespace `String` y secreto `byte[]` de al menos 32 bytes.
- Produces: `RateLimitKeyEncoder.Keys encode(String clientAddress, String username)`; `Keys` expone `ip()`, `pair()`, `account()` y `asList()`.

- [ ] **Step 1: Escribir pruebas fallidas del encoder**

Cubrir:

```java
@Test void shouldGenerateDeterministicKeys()
@Test void shouldNormalizeUsernameAndAddress()
@Test void shouldNotExposeAddressOrUsername()
@Test void shouldSeparateIpPairAndAccountDimensions()
@Test void shouldRejectSecretsShorterThan32Bytes()
```

Verificar el patrón completo:

```java
assertThat(keys.ip()).matches("tienda:auth:rate-limit:v1:ip:[0-9a-f]{64}");
assertThat(keys.pair()).doesNotContain("192.0.2.10", "admin@example.com");
```

- [ ] **Step 2: Ejecutar y confirmar el fallo**

```powershell
.\gradlew.bat test --tests "*RateLimitKeyEncoderTest" --no-daemon
```

Expected: FAIL porque `RateLimitKeyEncoder` no existe.

- [ ] **Step 3: Implementar HMAC-SHA-256 sin estado mutable compartido**

La clase debe:

- copiar defensivamente el secreto;
- normalizar con `trim().toLowerCase(Locale.ROOT)` y fallback `unknown`;
- crear una instancia `Mac` por digest porque `Mac` no es thread-safe;
- firmar `ip\0valor`, `pair\0ip\0username` y `account\0username`;
- convertir con `HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)))`;
- no implementar `toString()` con el secreto.

La API pública exacta será:

```text
Constructor: RateLimitKeyEncoder(String namespace, byte[] secret)
Método: RateLimitKeyEncoder.Keys encode(String clientAddress, String username)
Record: Keys(String ip, String pair, String account)
Método del record: List<String> asList()
```

`asList()` debe retornar `List.of(ip, pair, account)` en ese orden.

- [ ] **Step 4: Ejecutar pruebas**

```powershell
.\gradlew.bat test --tests "*RateLimitKeyEncoderTest" --no-daemon
```

Expected: PASS; ninguna aserción o mensaje de fallo imprime el secreto.

- [ ] **Step 5: Commit**

```powershell
git add apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/RateLimitKeyEncoder.java apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/out/security/RateLimitKeyEncoderTest.java
git commit -m "feat(identity): derive opaque login rate limit keys"
```

---

### Task 4: Implementar Scripts Lua Y El Adaptador Redis

**Files:**
- Create: `apps/backend/src/main/resources/redis/check-rate-limit.lua`
- Create: `apps/backend/src/main/resources/redis/clear-successful-login-reservation.lua`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/application/model/LoginRateLimitDimension.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/application/exception/LoginRateLimitedException.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/application/exception/LoginRateLimitUnavailableException.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/LoginRateLimitMetrics.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/RedisLoginRateLimiter.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/config/LoginRateLimitConfiguration.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/out/security/RedisLoginRateLimiterTest.java`

**Interfaces:**
- Consumes: `StringRedisTemplate`, `RedisScript<String> checkScript`, `RedisScript<Long> failureScript`, propiedades, encoder y métricas.
- Produces: implementación completa de `LoginRateLimitPort`; errores `LoginRateLimitedException(long, LoginRateLimitDimension)` y `LoginRateLimitUnavailableException(long)`.

- [ ] **Step 1: Escribir pruebas unitarias fallidas del adaptador**

Con `StringRedisTemplate` mockeado, cubrir:

```java
@Test void shouldAllowWhenScriptReturnsAllowedDecision()
@Test void shouldThrowRateLimitedWithDimensionAndRetryAfter()
@Test void shouldRecordAllThreeFailureDimensions()
@Test void shouldClearOnlyPairAndAccountAfterSuccess()
@Test void shouldTranslateDataAccessFailureToUnavailable()
```

El resultado de `check-rate-limit.lua` será `"1:0:0"` para permitido y `"0:37:2"` para bloqueo de par. El índice `1/2/3` mapea a `IP/PAIR/ACCOUNT`.

- [ ] **Step 2: Ejecutar y confirmar el fallo**

```powershell
.\gradlew.bat test --tests "*RedisLoginRateLimiterTest" --no-daemon
```

Expected: FAIL por clases y scripts inexistentes.

- [ ] **Step 3: Escribir scripts atómicos**

`check-rate-limit.lua` debe leer `KEYS[1..3]`, comparar con `ARGV[1..3]`, consultar `PTTL`, elegir el mayor TTL bloqueado y devolver `allowed:retryAfterSeconds:dimensionIndex`. Si una clave bloqueada carece de TTL, usar `ARGV[4]` como ventana defensiva.

Cuando la decisión sea permitida, `check-rate-limit.lua` debe ejecutar para las tres claves dentro del mismo script:

```lua
local value = redis.call('INCR', KEYS[index])
if value == 1 then
    redis.call('PEXPIRE', KEYS[index], ARGV[1])
end
```

El script retorna `1` después de procesar las tres dimensiones. No usar comandos separados desde Java para `INCR` y `EXPIRE`.

- [ ] **Step 4: Implementar el adaptador y métricas**

Crear:

```java
public enum LoginRateLimitDimension { IP, PAIR, ACCOUNT }
```

`LoginRateLimitUnavailableException` guarda `retryAfterSeconds` normalizado a mínimo uno. `LoginRateLimitMetrics` registra exactamente:

```text
auth.rate.limit.allowed(provider)
auth.rate.limit.blocked(provider,dimension)
auth.rate.limit.backend.errors(provider,operation)
auth.rate.limit.redis.latency(operation)
```

Los únicos valores de `operation` son `check`, `failure` y `success`; los únicos proveedores son `memory` y `redis`.

`RedisLoginRateLimiter` debe envolver cada llamada Redis en timer, capturar `DataAccessException`, registrar el error y lanzar `LoginRateLimitUnavailableException(properties.unavailableRetryAfter().toSeconds())`. No registrar keys, dirección, username ni hashes.

- [ ] **Step 5: Declarar scripts y encoder como beans singleton**

En `LoginRateLimitConfiguration` cargar los recursos mediante `ClassPathResource` y `DefaultRedisScript`:

```java
@Bean RedisScript<String> checkLoginRateLimitScript()
@Bean RedisScript<Long> recordLoginFailureScript()
@Bean
@ConditionalOnProperty(
    name = "app.security.login-rate-limit.provider",
    havingValue = "redis"
)
RateLimitKeyEncoder rateLimitKeyEncoder(LoginRateLimitProperties properties) {
    return new RateLimitKeyEncoder(
        properties.namespace(),
        Base64.getDecoder().decode(properties.keySecretBase64())
    );
}
```

Decodificar el secreto una sola vez al crear el encoder. No conservar la cadena Base64 fuera de properties.

- [ ] **Step 6: Ejecutar pruebas unitarias**

```powershell
.\gradlew.bat test --tests "*RedisLoginRateLimiterTest" --tests "*RateLimitKeyEncoderTest" --no-daemon
```

Expected: PASS; `DataAccessException` nunca escapa del adaptador.

- [ ] **Step 7: Commit**

```powershell
git add apps/backend/src/main/java/com/odcc/tienda/modules/identity apps/backend/src/main/resources/redis apps/backend/src/test/java/com/odcc/tienda/modules/identity
git commit -m "feat(identity): add atomic Redis login rate limiter"
```

---

### Task 5: Probar Redis Real, Concurrencia Y Dos Instancias

**Files:**
- Create: `apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/out/security/RedisLoginRateLimiterIntegrationTest.java`
- Create: `apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/out/security/RedisLoginRateLimiterUnavailableIntegrationTest.java`

**Interfaces:**
- Consumes: `redis:8.8.1-alpine`, `GenericContainer`, `LettuceConnectionFactory`, dos objetos independientes `RedisLoginRateLimiter`.
- Produces: evidencia automatizada para CA-001, CA-002, CA-003 y CA-004.

- [ ] **Step 1: Crear el fixture Redis autenticado**

Usar:

```java
@Container
static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8.1-alpine")
    .withExposedPorts(6379)
    .withCommand("redis-server", "--save", "", "--appendonly", "no", "--requirepass", REDIS_PASSWORD)
    .waitingFor(Wait.forHealthcheck());
```

Crear `LettuceConnectionFactory` apuntando a host y mapped port, con contraseña y timeouts de dos/uno segundos. Llamar `afterPropertiesSet()` y `start()`; cerrar la factory en `@AfterAll`.

- [ ] **Step 2: Escribir pruebas fallidas de integración**

Cubrir:

```java
@Test void twoLimiterInstancesShouldShareCounters()
@Test void concurrentFailuresShouldNotLoseIncrements()
@Test void shouldBlockEachDimensionAtItsOwnThreshold()
@Test void ttlShouldExpireAndAllowAgain()
@Test void successfulLoginShouldClearPairAndAccountButKeepIp()
@Test void storedKeysShouldBeOpaque()
```

La prueba concurrente usa `CountDownLatch`, `ExecutorService` y 20 fallos simultáneos; después verifica el bloqueo por IP. La inspección de claves acepta únicamente `tienda:auth:rate-limit:v1:(ip|pair|account):[0-9a-f]{64}` y rechaza cualquier coincidencia con la IP o cuenta de prueba.

- [ ] **Step 3: Ejecutar y ajustar únicamente errores reales**

```powershell
.\gradlew.bat test --tests "*RedisLoginRateLimiterIntegrationTest" --no-daemon
```

Expected inicial: FAIL si los scripts, parseo o TTL no satisfacen la semántica real. Corregir el código mínimo del adaptador/script y repetir hasta PASS.

- [ ] **Step 4: Probar indisponibilidad sin fallback**

En la segunda clase levantar un contenedor propio, crear el adaptador, detener el contenedor y verificar:

```java
assertThrows(LoginRateLimitUnavailableException.class,
    () -> limiter.check("192.0.2.40", "admin"));
```

En la misma prueba verificar que el bean configurado con provider `redis` sigue siendo `RedisLoginRateLimiter`; no crear ni consultar un `InMemoryLoginRateLimiter` como alternativa.

- [ ] **Step 5: Ejecutar ambas pruebas**

```powershell
.\gradlew.bat test --tests "*RedisLoginRateLimiter*IntegrationTest" --no-daemon
```

Expected: PASS con Docker disponible; no existen contadores sin TTL.

- [ ] **Step 6: Commit**

```powershell
git add apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/out/security apps/backend/src/main/resources/redis apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security
git commit -m "test(identity): verify distributed Redis rate limiting"
```

---

### Task 6: Cerrar El Contrato HTTP Y El Orden Del Login

**Files:**
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/application/usecase/LoginService.java:70-73`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/in/rest/error/AuthenticationExceptionHandler.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/in/rest/AuthenticationController.java:37-41`
- Modify: `apps/backend/src/test/java/com/odcc/tienda/modules/identity/application/usecase/LoginServiceTest.java`
- Modify: `apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/in/rest/AuthenticationControllerTest.java`

**Interfaces:**
- Consumes: `LoginRateLimitUnavailableException.retryAfterSeconds()` y `CorrelationIdFilter.from(request)`.
- Produces: `429 LOGIN_RATE_LIMITED` y `503 LOGIN_RATE_LIMIT_UNAVAILABLE`, ambos con `Retry-After`, `reason`, `path` y `correlationId`.

- [ ] **Step 1: Escribir pruebas REST fallidas**

Agregar a `AuthenticationControllerTest`:

```java
@Test void shouldReturn429WithRetryAfterAndCorrelationId()
@Test void shouldReturn503WhenRateLimitBackendIsUnavailable()
```

Enviar `X-Correlation-ID: login-rate-test-123` y verificar para `503`:

```java
.andExpect(status().isServiceUnavailable())
.andExpect(header().string("Retry-After", "5"))
.andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMIT_UNAVAILABLE"))
.andExpect(jsonPath("$.reason").value("Service Unavailable"))
.andExpect(jsonPath("$.correlationId").value("login-rate-test-123"));
```

- [ ] **Step 2: Escribir la prueba de orden fail-closed**

En `LoginServiceTest`, configurar credenciales válidas y hacer que `loginRateLimitPort.onSuccess(command.clientAddress(), username)` lance `LoginRateLimitUnavailableException`. Verificar:

```java
verify(accessTokenPort, never()).issue(any());
verify(authenticationAuditPort, never()).loginSucceeded(any(), any());
```

- [ ] **Step 3: Ejecutar y confirmar los fallos**

```powershell
.\gradlew.bat test --tests "*AuthenticationControllerTest" --tests "*LoginServiceTest" --no-daemon
```

Expected: al menos la respuesta `503` y el orden de emisión fallan.

- [ ] **Step 4: Implementar el handler y reordenar el login**

Agregar handler:

```java
@ExceptionHandler(LoginRateLimitUnavailableException.class)
public ResponseEntity<ApiResponseDto<Void>> handleRateLimitUnavailable(
    LoginRateLimitUnavailableException exception,
    HttpServletRequest request
) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
        .body(ApiResponseDto.error(
            HttpStatus.SERVICE_UNAVAILABLE,
            "LOGIN_RATE_LIMIT_UNAVAILABLE",
            exception.getMessage(),
            null,
            request.getRequestURI(),
            CorrelationIdFilter.from(request)
        ));
}
```

Actualizar también el `429` para pasar `CorrelationIdFilter.from(request)`. En `LoginService`, ejecutar `onSuccess` antes de `accessTokenPort.issue(user)`; así una caída Redis nunca crea un token que el cliente no recibe.

Agregar respuestas OpenAPI `429` y `503` a `AuthenticationController`.

- [ ] **Step 5: Ejecutar pruebas**

```powershell
.\gradlew.bat test --tests "*AuthenticationControllerTest" --tests "*LoginServiceTest" --no-daemon
```

Expected: PASS y el DTO conserva todos sus campos estándar.

- [ ] **Step 6: Commit**

```powershell
git add apps/backend/src/main/java/com/odcc/tienda/modules/identity apps/backend/src/test/java/com/odcc/tienda/modules/identity
git commit -m "fix(identity): fail closed when Redis login protection is unavailable"
```

---

### Task 7: Configurar Perfiles, Readiness Y Docker Compose

**Files:**
- Modify: `apps/backend/src/main/resources/application.yml`
- Modify: `apps/backend/src/main/resources/application-local.yml`
- Modify: `apps/backend/src/main/resources/application-prod.yml`
- Create: `apps/backend/src/main/resources/application-redis.yml`
- Modify: `apps/backend/src/test/resources/application.properties`
- Modify: `compose.yaml`
- Modify: `.env.example`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/config/LoginRateLimitProfileIntegrationTest.java`

**Interfaces:**
- Consumes: variables `LOGIN_RATE_LIMIT_*`, `RATE_LIMIT_KEY_SECRET_BASE64`, `REDIS_*`.
- Produces: perfil local/test sin Redis, perfil `redis` de validación y perfil `prod` fail-closed/readiness.

- [ ] **Step 1: Escribir pruebas de perfiles**

Crear pruebas de contexto que verifiquen:

```java
@Test void localProfileShouldStartWithoutRedis()
@Test void redisProfileShouldSelectRedisAdapter()
@Test void prodProfileShouldRejectMissingHmacSecret()
@Test void prodProfileShouldRejectMemoryOverride()
```

La primera no debe crear conexiones; la segunda usa Redis Testcontainers.

- [ ] **Step 2: Ejecutar y confirmar el fallo**

```powershell
.\gradlew.bat test --tests "*LoginRateLimitProfileIntegrationTest" --no-daemon
```

Expected: FAIL porque los perfiles todavía no definen el proveedor.

- [ ] **Step 3: Agregar propiedades base y perfiles exactos**

En `application.yml` definir defaults de memoria, límites, namespace, retry y conexión Redis. Mantener `management.health.redis.enabled: false` en base para que local no quede `DOWN` por un Redis no utilizado.

En `application-redis.yml` y `application-prod.yml`:

```yaml
app:
  security:
    login-rate-limit:
      provider: redis
      key-secret-base64: ${RATE_LIMIT_KEY_SECRET_BASE64}

management:
  health:
    redis:
      enabled: true
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,db,redis
```

En tests fijar `provider=memory` y límites `10000` para IP, par y cuenta.

- [ ] **Step 4: Añadir Redis al Compose sin exponer puerto**

Agregar servicio:

```yaml
redis:
  image: redis:8.8.1-alpine
  profiles: [distributed-security]
  environment:
    REDIS_PASSWORD: ${REDIS_PASSWORD:?Define REDIS_PASSWORD in .env}
  command:
    - sh
    - -c
    - exec redis-server --save '' --appendonly no --requirepass "$${REDIS_PASSWORD}"
  healthcheck:
    test: ["CMD-SHELL", "redis-cli --no-auth-warning -a \"$${REDIS_PASSWORD}\" ping | grep -q PONG"]
    interval: 5s
    timeout: 3s
    retries: 20
  restart: unless-stopped
  networks: [tienda_data]
```

No agregar `ports` ni `volumes`. En backend permitir `SPRING_PROFILES_ACTIVE`, host `redis`, password, connect timeout y command timeout mediante variables. El healthcheck del backend debe consultar `/actuator/health/readiness`; liveness queda disponible en `/actuator/health/liveness`.

- [ ] **Step 5: Documentar placeholders en `.env.example`**

Agregar:

```dotenv
LOGIN_RATE_LIMIT_PROVIDER=memory
LOGIN_RATE_LIMIT_WINDOW=PT1M
LOGIN_RATE_LIMIT_IP_MAX_FAILURES=20
LOGIN_RATE_LIMIT_PAIR_MAX_FAILURES=5
LOGIN_RATE_LIMIT_ACCOUNT_MAX_FAILURES=10
RATE_LIMIT_KEY_SECRET_BASE64=reemplazar_con_otro_secreto_base64_de_32_bytes
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=reemplazar_con_clave_redis_segura
REDIS_CONNECT_TIMEOUT=PT2S
REDIS_TIMEOUT=PT1S
```

El valor de `RATE_LIMIT_KEY_SECRET_BASE64` no puede ser igual a `JWT_SECRET_BASE64`.

- [ ] **Step 6: Ejecutar pruebas y validar Compose**

```powershell
.\gradlew.bat test --tests "*LoginRateLimitProfileIntegrationTest" --no-daemon
Set-Location ..\..
docker compose config --quiet
docker compose --profile distributed-security config --services
```

Expected: PASS; la segunda lista incluye `redis`, la primera operación normal no requiere levantarlo.

- [ ] **Step 7: Commit**

```powershell
git add apps/backend/src/main/resources apps/backend/src/test/resources apps/backend/src/test/java/com/odcc/tienda/modules/identity/adapter/config compose.yaml .env.example
git commit -m "chore(security): add Redis runtime profile and readiness"
```

---

### Task 8: Documentar Operación, Despliegue Y Rollback

**Files:**
- Create: `docs/decisions/ADR-0008-rate-limiting-distribuido.md`
- Create: `docs/operations/distributed-login-rate-limit.md`
- Modify: `docs/operations/ci-cd.md`
- Modify: `README.md`
- Modify: `PROJECT.md`

**Interfaces:**
- Consumes: perfiles, variables, métricas, endpoints health y workflows implementados.
- Produces: runbook reproducible para local, canary, caída controlada y rollback.

- [ ] **Step 1: Crear el ADR**

Registrar contexto, decisión Redis sobre `LoginRateLimitPort`, claves HMAC, Lua, fail-closed, consecuencias, alternativas rechazadas (memoria multiinstancia y KrakenD como sustituto del estado distribuido) y vínculo a la spec aprobada.

- [ ] **Step 2: Crear el runbook con comandos PowerShell exactos**

Incluir generación independiente de secretos:

```powershell
$jwtSecret = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$rateLimitSecret = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$redisPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
```

Incluir arranque distribuido:

```powershell
$env:SPRING_PROFILES_ACTIVE='local,redis'
$env:LOGIN_RATE_LIMIT_PROVIDER='redis'
docker compose --profile distributed-security up -d database redis backend
Invoke-RestMethod 'http://localhost:8080/actuator/health/readiness'
Invoke-RestMethod 'http://localhost:8080/actuator/health/liveness'
```

Incluir prueba de caída: detener únicamente Redis, verificar readiness `DOWN`, liveness `UP`, login `503`, JWT previamente emitido funcional; después restaurar Redis.

- [ ] **Step 3: Documentar métricas y alertas mínimas**

Listar los cuatro nombres Prometheus y recomendar alertas cuando `auth_rate_limit_backend_errors_total` aumente o readiness permanezca `DOWN` más de dos minutos. Aclarar que ningún tag identifica personas o direcciones.

- [ ] **Step 4: Actualizar CI y estado del proyecto**

En `ci-cd.md` sustituir la referencia `V034` por “última migración detectada automáticamente” y documentar Gitleaks, Dependency Review, Dependency Submission y Trivy filesystem. En README/PROJECT añadir Redis como dependencia de producción del login, no como dependencia de catálogo/ventas.

- [ ] **Step 5: Validar documentación**

```powershell
rg -n "REDIS_PASSWORD=.+[A-Za-z0-9]{32}|RATE_LIMIT_KEY_SECRET_BASE64=.+[A-Za-z0-9+/]{40}" docs README.md PROJECT.md .env.example
git diff --check
```

Expected: la primera búsqueda no encuentra secretos de apariencia real; `git diff --check` no reporta errores.

- [ ] **Step 6: Commit**

```powershell
git add docs README.md PROJECT.md
git commit -m "docs(security): document distributed login protection"
```

---

### Task 9: Validación Final Automatizada Y Operativa

**Files:**
- Modify only if a verified failure requires a correction in files from Tasks 1-8.

**Interfaces:**
- Consumes: toda la implementación y Docker Desktop.
- Produces: evidencia para CA-001 a CA-010 y candidato listo para PR.

- [ ] **Step 1: Ejecutar arquitectura y suite completa**

```powershell
Set-Location apps/backend
.\gradlew.bat compileJava --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat bootJar --no-daemon
```

Expected: todas las tareas terminan `BUILD SUCCESSFUL`; ArchUnit confirma que aplicación no depende de Redis/Spring.

- [ ] **Step 2: Validar Docker en modo memoria**

```powershell
Set-Location ..\..
docker compose --profile migrate run --rm flyway migrate
docker compose build backend
docker compose up -d database backend
Invoke-RestMethod 'http://localhost:8080/actuator/health'
```

Expected: `UP` sin servicio Redis y login local conserva el comportamiento actual.

- [ ] **Step 3: Validar Docker distribuido**

Configurar secretos distintos en `.env` ignorado, establecer `SPRING_PROFILES_ACTIVE=local,redis` y ejecutar:

```powershell
docker compose --profile distributed-security up -d redis backend
docker compose ps
Invoke-RestMethod 'http://localhost:8080/actuator/health/readiness'
```

Expected: Redis y backend healthy/readiness `UP`; Redis no publica puerto al host.

- [ ] **Step 4: Ejecutar pruebas HTTP de límite e indisponibilidad**

Enviar credenciales incorrectas desde la misma IP hasta obtener `429` y verificar `Retry-After`. Detener Redis:

```powershell
docker compose stop redis
```

Verificar nuevo login `503 LOGIN_RATE_LIMIT_UNAVAILABLE`, readiness `DOWN`, liveness `UP` y un endpoint protegido con JWT previamente emitido todavía funcional. Restaurar con:

```powershell
docker compose --profile distributed-security up -d redis
```

- [ ] **Step 5: Ejecutar controles locales de seguridad**

```powershell
gitleaks git --redact --log-opts="--all"
docker compose config --quiet
git diff --check
git status --short
```

Expected: cero secretos no permitidos, Compose válido, sin whitespace errors y solo cambios deliberados.

- [ ] **Step 6: Configurar controles manuales de GitHub**

En `GitHub > Settings > Code security and analysis`, habilitar `Dependency graph`, `Dependabot alerts`, `Secret scanning` y `Push protection`. En reglas de `main`, requerir los checks creados y prohibir merge si alguno falla. Registrar captura o URL de evidencia en el PR; no guardar tokens en el repositorio.

- [ ] **Step 7: Revisar los criterios de aceptación**

Adjuntar al PR:

```text
CA-001/002: RedisLoginRateLimiterIntegrationTest
CA-003: LoginRateLimitConfigurationTest + prueba de caída
CA-004: storedKeysShouldBeOpaque
CA-005: AuthenticationControllerTest
CA-006: suite completa en provider memory
CA-007: check Secret scan
CA-008: check Dependency review
CA-009: Backend CI + Filesystem security scan
CA-010: Push Protection + revisión de .env.example
```

- [ ] **Step 8: Commit de correcciones verificadas, si existen**

Si la validación exigió cambios, revisar interactivamente cada fragmento y crear un commit solo con las correcciones verificadas:

```powershell
git add -p
git diff --cached --check
git commit -m "fix(security): resolve distributed rate limit validation findings"
```

Si no hubo cambios, no crear un commit vacío.

- [ ] **Step 9: Preparar el PR sin hacer merge automático**

```powershell
git log --oneline --decorate main..HEAD
git diff --stat main...HEAD
git status --short
```

Expected: worktree limpio, commits lógicos y ninguna migración PostgreSQL. El merge requiere aprobación explícita del usuario.

---

## Orden De Despliegue

1. Integrar y verificar primero los controles CI.
2. Desplegar Redis privado con autenticación, TLS y alta disponibilidad en el ambiente objetivo.
3. Desplegar backend con provider `memory` fuera de producción y ejecutar smoke tests.
4. Activar `provider=redis` en una instancia canary.
5. Verificar `429`, TTL, métricas, readiness y la caída controlada `503`.
6. Activar las demás instancias y observar errores/latencia.

## Rollback

- Restaurar la versión anterior del backend; Redis puede permanecer temporalmente sin consumidores.
- No eliminar datos PostgreSQL ni ejecutar Flyway porque esta entrega no contiene migraciones.
- `provider=memory` no está permitido en producción; una incidencia Redis debe conservar el rechazo cerrado de nuevos logins.
- Un check CI solo se deshabilita mediante commit revisado; no usar `continue-on-error`.
