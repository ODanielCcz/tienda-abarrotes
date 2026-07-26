# API Backend v1

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | TDA-API-001 |
| Versión | v1.0 |
| Estado | En revisión |
| Responsable | Por definir |
| Fecha | 2026-07-25 |
| Clasificación | Interno |

## Control De Versiones

| Versión | Fecha | Autor | Cambio | Estado |
|---|---|---|---|---|
| v1.0 | 2026-07-25 | Equipo del proyecto | Contrato inicial de autenticación y marcas | En revisión |

## Resumen Del Servicio

API REST del backend Spring Boot para clientes web y móvil. El contrato ejecutable se publica mediante OpenAPI y todas las respuestas de negocio utilizan `ApiResponseDto<T>`.

## Base URL

```text
http://localhost:8080
```

## Autenticación

Obtén un token con `POST /api/v1/auth/login` y envíalo en las operaciones protegidas:

```http
Authorization: Bearer <accessToken>
```

El token es de acceso, no de refresco. Su duración predeterminada es 30 minutos y se configura con `JWT_ACCESS_TOKEN_TTL` en formato `Duration` ISO-8601.

## Convenciones

- Fechas y horas en UTC con formato ISO-8601.
- UUID para identificadores de recursos.
- `X-Correlation-ID` opcional en la petición; siempre se devuelve uno en la respuesta.
- Los códigos como `BRAND_FOUND` son estables para los clientes; el mensaje es informativo.
- No se elimina físicamente una marca: se activa o desactiva.

### Respuesta Común

```json
{
  "timestamp": "2026-07-25T20:00:00Z",
  "status": 200,
  "code": "BRAND_FOUND",
  "message": "Marca encontrada correctamente",
  "data": {},
  "errors": null,
  "path": "/api/v1/catalog/brands/00000000-0000-0000-0000-000000000000"
}
```

## Endpoints

### POST /api/v1/auth/login

| Campo | Valor |
|---|---|
| Descripción | Autentica un usuario activo y emite un JWT |
| Autenticación | No requerida |
| Permiso | No aplica |

#### Request

```json
{
  "username": "admin",
  "password": "contraseña-local"
}
```

#### Códigos De Estado

| HTTP | Código | Caso |
|---|---|---|
| 200 | `LOGIN_SUCCEEDED` | Credenciales válidas |
| 400 | `VALIDATION_ERROR` | Campos ausentes o inválidos |
| 401 | `INVALID_CREDENTIALS` | Usuario o contraseña incorrectos |
| 403 | `USER_NOT_ACTIVE` | Usuario deshabilitado o bloqueado |

### GET /api/v1/catalog/brands

| Campo | Valor |
|---|---|
| Descripción | Lista marcas con paginación, filtro y orden estable |
| Autenticación | Requerida |
| Permiso | `CATALOG_BRAND_READ` |

#### Parámetros

| Nombre | Tipo | Ubicación | Requerido | Descripción |
|---|---|---|---|---|
| `page` | entero | query | No | Página desde 0; predeterminado 0 |
| `size` | entero | query | No | Registros por página; predeterminado 20; máximo 100 |
| `search` | texto | query | No | Coincidencia parcial por código o nombre |
| `status` | enum | query | No | `ACTIVE` o `INACTIVE` |
| `sortBy` | enum | query | No | `CODE`, `NAME` o `CREATED_AT` |
| `direction` | enum | query | No | `ASC` o `DESC` |

#### Response Exitosa

```json
{
  "status": 200,
  "code": "BRANDS_FOUND",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  },
  "errors": null
}
```

### POST /api/v1/catalog/brands

| Campo | Valor |
|---|---|
| Descripción | Crea una marca activa |
| Autenticación | Requerida |
| Permiso | `CATALOG_BRAND_CREATE` |

```json
{
  "code": "coca-cola",
  "name": "Coca Cola"
}
```

Respuesta: `201 BRAND_CREATED`. Conflicto por código: `409 BRAND_CODE_ALREADY_EXISTS`.

### GET /api/v1/catalog/brands/{brandId}

| Campo | Valor |
|---|---|
| Descripción | Consulta una marca por UUID |
| Autenticación | Requerida |
| Permiso | `CATALOG_BRAND_READ` |

Respuesta: `200 BRAND_FOUND`. Si no existe: `404 BRAND_NOT_FOUND`.

### PUT /api/v1/catalog/brands/{brandId}

| Campo | Valor |
|---|---|
| Descripción | Reemplaza código y nombre dentro de una transacción |
| Autenticación | Requerida |
| Permiso | `CATALOG_BRAND_UPDATE` |

```json
{
  "code": "COCA-COLA-MX",
  "name": "Coca Cola México"
}
```

Respuesta: `200 BRAND_UPDATED`. Puede responder `404 BRAND_NOT_FOUND` o `409 BRAND_CODE_ALREADY_EXISTS`.

### PATCH /api/v1/catalog/brands/{brandId}/status

| Campo | Valor |
|---|---|
| Descripción | Activa o desactiva una marca y registra auditoría |
| Autenticación | Requerida |
| Permiso | `CATALOG_BRAND_STATUS` |

```json
{
  "status": "INACTIVE"
}
```

Respuesta: `200 BRAND_STATUS_UPDATED`. Si no existe: `404 BRAND_NOT_FOUND`.

### GET /api/v1/catalog/categories

| Campo | Valor |
|---|---|
| Descripcion | Lista categorias con paginacion, filtro y orden estable |
| Autenticacion | Requerida |
| Permiso | `CATALOG_CATEGORY_READ` |

#### Parametros

| Nombre | Tipo | Ubicacion | Requerido | Descripcion |
|---|---|---|---|---|
| `page` | entero | query | No | Pagina desde 0; predeterminado 0 |
| `size` | entero | query | No | Registros por pagina; predeterminado 20; maximo 100 |
| `search` | texto | query | No | Coincidencia parcial por codigo o nombre |
| `status` | enum | query | No | `ACTIVE` o `INACTIVE` |
| `sortBy` | enum | query | No | `CODE`, `NAME`, `CREATED_AT` o `UPDATED_AT` |
| `direction` | enum | query | No | `ASC` o `DESC` |

Respuesta: `200 CATEGORIES_FOUND`.

### POST /api/v1/catalog/categories

| Campo | Valor |
|---|---|
| Descripcion | Crea una categoria activa, opcionalmente hija de otra categoria |
| Autenticacion | Requerida |
| Permiso | `CATALOG_CATEGORY_CREATE` |

```json
{
  "code": "bebidas",
  "name": "Bebidas",
  "parentCategoryId": null
}
```

Respuesta: `201 CATEGORY_CREATED`. Conflicto por codigo: `409 CATEGORY_CODE_ALREADY_EXISTS`. Padre inexistente: `404 CATEGORY_PARENT_NOT_FOUND`.

### GET /api/v1/catalog/categories/{categoryId}

| Campo | Valor |
|---|---|
| Descripcion | Consulta una categoria por UUID |
| Autenticacion | Requerida |
| Permiso | `CATALOG_CATEGORY_READ` |

Respuesta: `200 CATEGORY_FOUND`. Si no existe: `404 CATEGORY_NOT_FOUND`.

### PUT /api/v1/catalog/categories/{categoryId}

| Campo | Valor |
|---|---|
| Descripcion | Reemplaza codigo, nombre y categoria padre dentro de una transaccion |
| Autenticacion | Requerida |
| Permiso | `CATALOG_CATEGORY_UPDATE` |

```json
{
  "code": "bebidas-frias",
  "name": "Bebidas frias",
  "parentCategoryId": "00000000-0000-0000-0000-000000000000"
}
```

Respuesta: `200 CATEGORY_UPDATED`. Puede responder `400 INVALID_CATEGORY`, `404 CATEGORY_NOT_FOUND`, `404 CATEGORY_PARENT_NOT_FOUND` o `409 CATEGORY_CODE_ALREADY_EXISTS`.

### PATCH /api/v1/catalog/categories/{categoryId}/status

| Campo | Valor |
|---|---|
| Descripcion | Activa o desactiva una categoria y registra auditoria |
| Autenticacion | Requerida |
| Permiso | `CATALOG_CATEGORY_STATUS` |

```json
{
  "status": "INACTIVE"
}
```

Respuesta: `200 CATEGORY_STATUS_UPDATED`. Si no existe: `404 CATEGORY_NOT_FOUND`.
## Errores Transversales

| HTTP | Código | Causa | Acción Sugerida |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Campos inválidos | Revisar `errors` por campo |
| 400 | `MALFORMED_JSON` | JSON ilegible | Corregir sintaxis y tipos |
| 400 | `INVALID_PARAMETER` | Parámetro incompatible | Usar los valores documentados |
| 400 | `MISSING_PARAMETER` | Parámetro obligatorio ausente | Enviar el parámetro indicado |
| 401 | `UNAUTHORIZED` | JWT ausente, vencido o inválido | Autenticarse de nuevo |
| 403 | `FORBIDDEN` | Falta el permiso requerido | Solicitar asignación de rol |
| 404 | `RESOURCE_NOT_FOUND` | Ruta inexistente | Verificar método y URL |
| 405 | `METHOD_NOT_ALLOWED` | Método HTTP no soportado | Usar el método documentado |
| 500 | `INTERNAL_ERROR` | Error no controlado | Reportar el `X-Correlation-ID` |

## Endpoints Operativos

| Método | Ruta | Autenticación | Uso |
|---|---|---|---|
| GET | `/actuator/health` | No | Salud de aplicación y dependencias |
| GET | `/actuator/info` | No | Información operativa |
| GET | `/actuator/metrics` | Sí | Inventario de métricas |
| GET | `/actuator/prometheus` | Sí | Scrape en formato Prometheus |
| GET | `/v3/api-docs` | No | Contrato OpenAPI JSON |
| GET | `/swagger-ui.html` | No | Interfaz navegable Swagger |

## Evidencia De Verificación

```powershell
# Desde apps/backend
.\gradlew.bat clean test --no-daemon
```

La suite cubre dominio, casos de uso, persistencia PostgreSQL, REST, transacciones, auditoría, autenticación, autorización, OpenAPI, CORS, métricas, trazas y reglas arquitectónicas.

## Pendientes

- Definir refresh tokens y revocación cuando se diseñen sesiones de usuario.
- Definir el colector OTLP y la política de retención antes de habilitar exportación en un entorno compartido.
