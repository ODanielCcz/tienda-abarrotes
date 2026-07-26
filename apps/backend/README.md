# Backend — Tienda De Abarrotes

Backend Spring Boot del sistema de tienda de abarrotes.

## Base Técnica

- Java 21.
- Spring Boot 4.1.0.
- Gradle Kotlin DSL con Gradle Wrapper.
- Arquitectura hexagonal organizada por módulos de negocio.
- Spring Data JPA y PostgreSQL para los adaptadores de persistencia.
- MapStruct y Lombok para reducir mapeos y código repetitivo.
- Testcontainers con PostgreSQL real para pruebas de integración.
- Spring Security con JWT, roles y permisos persistidos en PostgreSQL.
- OpenAPI/Swagger, métricas Prometheus y trazas OpenTelemetry.

## Ejecución

```powershell
.\gradlew.bat bootRun
```

La aplicación inicia en `http://localhost:8080`. El endpoint técnico inicial es:

```text
GET http://localhost:8080/actuator/health
```

El perfil predeterminado es `local` y carga opcionalmente el archivo `../../.env`.

Para un entorno local nuevo, inicia primero la base y aplica las migraciones:

```powershell
# Desde la raíz del monorepo
docker compose up -d database
docker compose --profile migrate run --rm flyway migrate

# Desde apps/backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local" --no-daemon
```

## Perfiles

| Perfil | Uso | Credenciales |
|---|---|---|
| `local` | Desarrollo en la computadora del programador | Lee el archivo local ignorado `.env` |
| `dev` | Ambiente de desarrollo compartido | Exige variables de entorno |
| `prod` | Producción | Exige variables de entorno y no muestra detalles de salud |
| `test` | Pruebas automatizadas | PostgreSQL efímero mediante Testcontainers |

Para activar un perfil:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
.\gradlew.bat bootRun --args="--spring.profiles.active=prod"
```

Los perfiles `dev` y `prod` requieren:

```text
POSTGRES_HOST
POSTGRES_PORT
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
JWT_SECRET_BASE64
CORS_ALLOWED_ORIGINS
```

`JWT_SECRET_BASE64` debe representar al menos 32 bytes aleatorios. Ejemplo de generación en PowerShell:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

No guardes el resultado en Git. Para crear el primer administrador únicamente en `local`, configura `BOOTSTRAP_ADMIN_ENABLED=true`, usuario y contraseña en el `.env` ignorado; el bootstrap no almacena la contraseña en texto plano.

## API Y Seguridad

- Login público: `POST /api/v1/auth/login`.
- Swagger UI público: `http://localhost:8080/swagger-ui.html`.
- Contrato OpenAPI: `http://localhost:8080/v3/api-docs`.
- Las operaciones de marcas requieren `Authorization: Bearer <token>` y el permiso correspondiente.
- Salud e información son públicas; métricas Prometheus requieren autenticación.

La guía completa de consumo está en [`docs/api/backend-api-v1.md`](../../docs/api/backend-api-v1.md).

## Observabilidad

- `X-Correlation-ID` se acepta o genera en cada petición y también se devuelve en la respuesta.
- Los logs incluyen aplicación, `traceId`, `spanId` y `correlationId`.
- El perfil `prod` emite logs JSON en formato Logstash.
- Métricas: `GET /actuator/prometheus` con JWT válido.
- La exportación OTLP está apagada por defecto. Se activa con `OTLP_TRACING_ENABLED=true` y `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`.

## Verificación

```powershell
.\gradlew.bat clean test --no-daemon
```

La suite incluye pruebas unitarias de dominio y casos de uso, adaptadores REST, persistencia PostgreSQL, transacciones, auditoría, seguridad 401/403, OpenAPI, métricas, trazas y reglas ArchUnit.

## Regla Arquitectónica

Cada módulo de negocio se implementará con esta forma:

```text
<modulo>/
├── domain/
│   ├── model/                 # Entidades, agregados y objetos de valor
│   ├── service/               # Políticas puras de dominio
│   └── event/                 # Eventos del dominio
├── application/
│   ├── port/
│   │   ├── in/                # Contratos de entrada o casos de uso
│   │   └── out/               # Contratos requeridos por la aplicación
│   ├── usecase/               # Implementación y orquestación
│   ├── command/               # Solicitudes que cambian estado
│   └── query/                 # Solicitudes de lectura
└── adapter/
    ├── in/
    │   └── rest/              # Controladores y DTO HTTP
    └── out/
        ├── persistence/       # JPA, repositorios y mapeadores
        └── integration/       # Clientes de servicios externos
```

La regla de dependencias es:

```text
adapter -> application -> domain
```

El dominio no depende de Spring, HTTP, JPA, PostgreSQL ni SDK externos.

## Módulos Previstos

- `identity`: usuarios, roles y permisos.
- `organization`: sucursales, almacenes y cajas.
- `catalog`: productos, marcas, categorías y unidades.
- `inventory`: existencias, movimientos, lotes y reservas.
- `purchasing`: proveedores y compras.
- `sales`: clientes, ventas, pagos y crédito.
- `cash`: sesiones y movimientos de caja.
- `billing`: información fiscal y CFDI.
- `audit`: auditoría funcional.
- `sync`: sincronización móvil e idempotencia.

Las carpetas internas de cada módulo se crearán cuando exista el primer caso de uso real. Esto evita paquetes vacíos y abstracciones sin necesidad.

## Persistencia

Spring Data JPA y el driver PostgreSQL están disponibles para implementar adaptadores de salida. Hibernate utiliza `ddl-auto=validate`: valida los mapeos, pero no crea ni modifica tablas.

Flyway continúa ejecutándose como proceso independiente desde la raíz del repositorio. Los cambios de esquema se mantienen exclusivamente en `database/migrations`.
