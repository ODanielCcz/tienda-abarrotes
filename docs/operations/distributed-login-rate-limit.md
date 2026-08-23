# Operación Del Rate Limiting Distribuido De Login

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | OPS-SEC-LOGIN-001 |
| Versión | v1.0 |
| Estado | Aprobado |
| Responsable | Equipo backend / Operaciones |
| Fecha | 2026-08-23 |
| Clasificación | Interno |

## Control De Versiones

| Versión | Fecha | Autor | Cambio | Estado |
|---|---|---|---|---|
| v1.0 | 2026-08-23 | Equipo backend | Runbook inicial de Redis para protección del login | Aprobado |

## Propósito

Describir la configuración, validación, monitoreo, recuperación y rollback del limitador distribuido de inicio de sesión. Este procedimiento no almacena valores reales de secretos en Git.

## Arquitectura General

```text
Cliente → POST /api/v1/auth/login → LoginRateLimitPort
                                      ├── memory (local/test)
                                      └── Redis + Lua (redis/prod)
```

Redis solo contiene contadores opacos con TTL. El JWT permanece autocontenido y PostgreSQL conserva usuarios, roles y auditoría.

## Requisitos Previos

- Docker Desktop activo.
- `.env` local ignorado por Git.
- Redis privado para producción; el Compose local no publica `6379`.
- Tres secretos independientes: JWT, HMAC de claves y contraseña Redis.

## Generación De Secretos

Ejecutar en PowerShell y guardar los resultados únicamente en el gestor de secretos o `.env` local:

```powershell
$jwtSecret = [Convert]::ToBase64String(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(48)
)
$rateLimitSecret = [Convert]::ToBase64String(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)
$redisPassword = [Convert]::ToHexString(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
).ToLowerInvariant()
```

`$jwtSecret` y `$rateLimitSecret` deben ser distintos.

## Configuración

| Variable | Obligatoria en Redis | Valor recomendado |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Sí | `local,redis` en validación; `prod` en producción |
| `LOGIN_RATE_LIMIT_PROVIDER` | Sí | `redis` |
| `RATE_LIMIT_KEY_SECRET_BASE64` | Sí | Base64 de 32 bytes o más |
| `REDIS_HOST` | Sí | DNS privado del servicio |
| `REDIS_PORT` | Sí | `6379` o puerto TLS administrado |
| `REDIS_PASSWORD` | Sí | Secreto aleatorio independiente |
| `REDIS_CONNECT_TIMEOUT` | No | `PT2S` |
| `REDIS_TIMEOUT` | No | `PT1S` |

Los umbrales predeterminados son 20 por IP, 5 por IP+cuenta y 10 por cuenta en una ventana de un minuto.

## Ejecución Local En Memoria

```powershell
docker compose up -d database backend
Invoke-RestMethod 'http://localhost:8080/actuator/health/readiness'
```

El servicio Redis no es necesario en este modo.

## Ejecución Local Distribuida

Después de completar `.env`:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local,redis'
$env:LOGIN_RATE_LIMIT_PROVIDER = 'redis'

docker compose --profile distributed-security up -d database redis backend
docker compose ps

Invoke-RestMethod 'http://localhost:8080/actuator/health/readiness'
Invoke-RestMethod 'http://localhost:8080/actuator/health/liveness'
```

Confirmar que Redis no publica puertos:

```powershell
docker compose --profile distributed-security port redis 6379
```

El comando no debe devolver una dirección del host.

## Prueba De Límites

Enviar credenciales incorrectas repetidamente desde la misma dirección. El umbral aplicable debe devolver:

```text
HTTP 429
Retry-After: <segundos>
code: LOGIN_RATE_LIMITED
```

La respuesta conserva `reason`, `path` y `correlationId`.

## Prueba De Caída Controlada

1. Iniciar sesión correctamente y conservar un JWT de prueba.
2. Detener solo Redis:

```powershell
docker compose stop redis
```

3. Verificar los resultados:

```powershell
curl.exe -i http://localhost:8080/actuator/health/readiness
curl.exe -i http://localhost:8080/actuator/health/liveness

curl.exe -i -X POST http://localhost:8080/api/v1/auth/login `
  -H 'Content-Type: application/json' `
  -H 'X-Correlation-ID: redis-down-test' `
  -d '{"username":"admin","password":"valor-de-prueba"}'
```

| Comprobación | Resultado esperado |
|---|---|
| Readiness | `DOWN` |
| Liveness | `UP` |
| Login nuevo | `503 LOGIN_RATE_LIMIT_UNAVAILABLE`, `Retry-After: 5` |
| Endpoint con JWT previo | Continúa autenticando mientras el token sea válido |

4. Restaurar Redis:

```powershell
docker compose --profile distributed-security up -d redis
Invoke-RestMethod 'http://localhost:8080/actuator/health/readiness'
```

## Métricas Y Alertas

| Métrica Micrometer | Nombre Prometheus | Uso |
|---|---|---|
| `auth.rate.limit.allowed` | `auth_rate_limit_allowed_total` | Solicitudes permitidas por proveedor |
| `auth.rate.limit.blocked` | `auth_rate_limit_blocked_total` | Bloqueos por proveedor y dimensión |
| `auth.rate.limit.backend.errors` | `auth_rate_limit_backend_errors_total` | Fallos del backend de rate limit |
| `auth.rate.limit.redis.latency` | `auth_rate_limit_redis_latency_seconds` | Latencia por operación Redis |

Alertar cuando aumente `auth_rate_limit_backend_errors_total` o readiness permanezca `DOWN` más de dos minutos. Ninguna etiqueta incluye IP, username, clave Redis o hash completo.

## Despliegue

1. Aprovisionar Redis privado, autenticado, con TLS y alta disponibilidad.
2. Desplegar primero con memoria fuera de producción y ejecutar smoke tests.
3. Activar `redis` en una instancia canary.
4. Verificar 429, TTL, métricas, readiness y caída 503.
5. Activar las demás instancias y observar latencia y errores.

## Rollback

- Restaurar la versión anterior del backend.
- Redis puede permanecer activo temporalmente sin consumidores.
- No eliminar datos PostgreSQL ni ejecutar `Flyway clean`.
- Usar memoria en producción únicamente como mitigación temporal, documentada y con una sola instancia.
- No deshabilitar checks de seguridad mediante `continue-on-error`.

## Solución De Problemas

| Síntoma | Diagnóstico | Acción |
|---|---|---|
| Backend no inicia en `prod` | Provider distinto de Redis o secreto HMAC inválido | Corregir perfil y secreto Base64 de 32 bytes |
| Readiness `DOWN`, liveness `UP` | Redis no disponible | Revisar DNS, contraseña, TLS y healthcheck Redis |
| Login devuelve 503 | El adaptador no pudo consultar Redis | Restaurar Redis; no habilitar fallback silencioso |
| Bloqueos persisten más de la ventana | TTL o reloj operativo incorrecto | Consultar TTL de claves de prueba y reiniciar Redis si procede |
| Redis visible desde el host | Configuración ajena al Compose aprobado | Retirar `ports` y limitar la red antes de continuar |
