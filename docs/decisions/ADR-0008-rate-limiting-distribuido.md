# ADR-0008: Rate Limiting Distribuido Para Inicio De Sesión

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | ADR-0008 |
| Versión | v1.0 |
| Estado | Aceptada |
| Responsable | Equipo backend |
| Fecha | 2026-08-23 |
| Clasificación | Interno |

## Control De Versiones

| Versión | Fecha | Autor | Cambio | Estado |
|---|---|---|---|---|
| v1.0 | 2026-08-23 | Equipo backend | Decisión inicial de protección distribuida del login | Aceptada |

## Contexto

El limitador en memoria protege una sola instancia, pero no comparte intentos entre réplicas. Un atacante podría distribuir solicitudes entre instancias y superar el umbral efectivo. La protección debe conservar la arquitectura hexagonal y no exponer IP, username ni credenciales en el almacén distribuido o en métricas.

## Decisión

- `LoginService` continúa dependiendo exclusivamente de `LoginRateLimitPort`.
- El perfil local y la suite general utilizan el adaptador en memoria.
- El perfil de producción exige el adaptador Redis; no existe fallback silencioso.
- Redis almacena tres contadores temporales: IP, IP+cuenta y cuenta.
- Las claves se derivan con HMAC-SHA-256 y un secreto distinto del secreto JWT.
- Dos scripts Lua realizan la consulta y el incremento/TTL de forma atómica.
- Un login correcto elimina los contadores de IP+cuenta y cuenta, pero conserva el contador por IP.
- Una caída de Redis devuelve `503 LOGIN_RATE_LIMIT_UNAVAILABLE`, mantiene liveness en `UP` y cambia readiness a `DOWN`.
- Redis no almacena sesiones ni JWT y no participa en los módulos comerciales.
- Las métricas usan etiquetas de baja cardinalidad sin identificadores personales.

## Consecuencias

### Positivas

- Todas las instancias aplican los mismos umbrales.
- Los incrementos concurrentes no se pierden.
- Las claves Redis no revelan IP ni cuenta.
- El backend no entrega un token cuando no puede confirmar la protección del login.
- El fallo se observa mediante readiness, métricas y respuesta trazable.

### Costos Y Restricciones

- Producción necesita Redis privado, autenticado, con TLS y alta disponibilidad.
- La disponibilidad del login depende de Redis; los JWT ya emitidos siguen funcionando.
- La rotación del secreto HMAC invalida de forma natural los contadores anteriores.

## Alternativas Rechazadas

| Alternativa | Motivo de rechazo |
|---|---|
| Memoria por instancia | No coordina réplicas y reduce el límite efectivo al escalar. |
| Base PostgreSQL | Añade contención y carga de escritura al almacén transaccional principal. |
| KrakenD como sustituto | Puede limitar tráfico perimetral, pero no conserva el estado IP+cuenta y cuenta exigido por autenticación. |
| Fallback automático a memoria | Oculta una degradación de seguridad y permite límites inconsistentes. |

## Trazabilidad

- Especificación: `docs/superpowers/specs/2026-08-23-distributed-login-rate-limit-and-security-pipeline-design.md`.
- Plan: `docs/superpowers/plans/2026-08-23-distributed-login-rate-limit-and-security-pipeline.md`.
- Operación: `docs/operations/distributed-login-rate-limit.md`.
