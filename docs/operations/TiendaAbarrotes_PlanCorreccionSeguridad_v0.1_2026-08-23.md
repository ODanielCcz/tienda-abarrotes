# Plan De Corrección De Seguridad Del Backend

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | ODCC-SEG-PLAN-001 |
| Versión | v0.1 |
| Estado | Borrador |
| Responsable | Equipo de desarrollo ODCC |
| Fecha | 2026-08-23 |
| Clasificación | Interno |

## Control De Versiones

| Versión | Fecha | Autor | Cambio | Estado |
|---|---|---|---|---|
| v0.1 | 2026-08-23 | Codex | Creación del plan de remediación para los hallazgos del barrido del backend | Borrador |

## 1. Propósito

Definir el orden de trabajo, los cambios técnicos, las pruebas y los criterios de aceptación necesarios para corregir los ocho hallazgos detectados durante el barrido estático de seguridad del backend.

El objetivo es proteger la integridad financiera y de inventario, mejorar la resistencia ante concurrencia y abuso, fortalecer la autenticación y asegurar que las consultas respeten correctamente el alcance por sucursal.

## 2. Alcance

Incluye:

- Backend Spring Boot ubicado en `apps/backend`.
- Casos de uso, puertos y adaptadores afectados.
- Migraciones Flyway estrictamente necesarias.
- Pruebas unitarias, integración, seguridad y concurrencia.
- Documentación técnica relacionada.

No incluye:

- Angular ni aplicación móvil.
- Timbrado fiscal o conexión con PAC.
- Cambios comerciales no relacionados con los hallazgos.
- Redis, gateway o nueva infraestructura distribuida.
- Correcciones automáticas de dependencias sin validar compatibilidad.

## 3. Línea Base

| Elemento | Valor |
|---|---|
| Revisión analizada | `e1313d2106a17ec9c2744e7753d5c017eb22547d` |
| Alcance del barrido | `apps/backend` |
| Hallazgos medios | 2 |
| Hallazgos bajos | 6 |
| Hallazgos críticos/altos | 0 |
| Inyección SQL directa validada | No detectada |
| Tipo de revisión | Estática; falta validación dinámica y concurrente |

Antes de implementar se debe confirmar que la rama de trabajo parte de la revisión aprobada y que no contiene cambios ajenos sin guardar.

## 4. Matriz De Hallazgos

| ID | Severidad | Hallazgo | Riesgo principal | Prioridad |
|---|---|---|---|---|
| SEG-001 | Media | Reembolso excesivo en pagos mixtos | Pérdida financiera y caja incorrecta | P0 |
| SEG-002 | Media | Carrera en recepción idempotente de compras | Cantidad recibida duplicada | P0 |
| SEG-003 | Baja | Carrera en idempotencia de ventas | Error 500 y reintentos no confiables | P1 |
| SEG-004 | Baja | Colecciones transaccionales sin límite | Agotamiento de memoria, CPU o BD | P1 |
| SEG-005 | Baja | Enumeración de usuarios por tiempo | Descubrimiento de cuentas válidas | P1 |
| SEG-006 | Baja | Bloqueo intencional de cuentas | Denegación temporal de acceso | P1 |
| SEG-007 | Baja | Contraseñas débiles desde API | Mayor probabilidad de compromiso | P1 |
| SEG-008 | Baja | Alcance de sucursal aplicado después de `LIMIT` | Listados incompletos | P2 |

## 5. Orden De Implementación

1. Preparar la línea base y las pruebas que reproducen los errores.
2. Corregir reembolsos de pagos mixtos.
3. Corregir idempotencia de recepciones de compra.
4. Corregir idempotencia concurrente de ventas.
5. Limitar cuerpos y colecciones de entrada.
6. Fortalecer autenticación y política de contraseñas.
7. Mover el filtro de sucursal a las consultas SQL.
8. Ejecutar validación completa, pruebas HTTP y revisión de regresión.

Las pruebas de reproducción deben escribirse antes de cada corrección y fallar con el comportamiento actual. Después se implementará el cambio mínimo que las deje en verde.

## 6. Fase 0: Preparación Y Pruebas De Reproducción

### Actividades

- Crear una rama con nombre funcional, por ejemplo `security/backend-hardening`.
- Ejecutar CodeGraph antes de modificar cada flujo para identificar puertos, implementaciones y consumidores.
- Confirmar el estado de Git y preservar cambios ajenos.
- Ejecutar compilación y suite completa para obtener una línea base.
- Crear pruebas de regresión para cada hallazgo antes de modificar la lógica productiva.

### Comandos De Línea Base

```powershell
cd "C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes\apps\backend"

git status --short
git rev-parse --short HEAD
codegraph explore "SalesReturn PurchaseService InventoryReceipt SalesOrder LoginService BranchScope"

.\gradlew.bat compileJava --no-daemon
.\gradlew.bat test --no-daemon
```

### Criterios De Aceptación

- La revisión base y los cambios preexistentes quedan documentados.
- Cada hallazgo cuenta con al menos una prueba que reproduce el comportamiento vulnerable o defectuoso.
- No se modifica Angular ni móvil.

## 7. Fase 1: Integridad Financiera Y Concurrencia

### 7.1 SEG-001: Reembolsos Correctos Para Pagos Mixtos

#### Problema

La confirmación de una devolución busca cualquier pago `CASH` capturado y registra el total completo de la devolución como salida de efectivo. Una venta pagada con 10 MXN en efectivo y 90 MXN con tarjeta podría generar un reembolso de 100 MXN en efectivo.

#### Cambios Propuestos

- Crear un modelo explícito de asignación de reembolsos por pago original.
- Incorporar una tabla como `sales.refund_allocations` si el esquema actual no permite conservar la trazabilidad:
  - `refund_allocation_id`.
  - `return_id`.
  - `payment_id`.
  - `payment_method`.
  - `amount`.
  - `cash_movement_id`, opcional.
  - `created_at`.
- Agregar restricciones para impedir importes negativos y duplicados por fuente.
- Calcular por cada pago:

```text
saldo reembolsable = importe capturado - reembolsos confirmados anteriores
```

- Distribuir el importe de la devolución sin superar el saldo de cada pago.
- Crear movimiento `REFUND OUT` únicamente por la porción asignada a `CASH`.
- Mantener tarjeta y transferencia como reembolsos lógicos pendientes o confirmados según el estado soportado, sin alterar efectivo.
- Ejecutar asignaciones, actualización de venta, movimiento de caja y confirmación de devolución dentro del mismo `TransactionRunner`.

#### Pruebas

- Venta 100 % en efectivo: devolución total genera exactamente el total en `REFUND OUT`.
- Venta 10 efectivo + 90 tarjeta: devolución total genera como máximo 10 en efectivo.
- Varias devoluciones parciales nunca superan lo capturado por método.
- Dos confirmaciones concurrentes no duplican asignaciones ni movimientos.
- Un fallo al crear cualquier asignación produce rollback completo.

#### Criterios De Aceptación

- La suma reembolsada por pago nunca supera su importe capturado.
- Todo movimiento de caja queda vinculado a una asignación y devolución.
- No se genera salida de efectivo por importes originalmente pagados con métodos no efectivos.

### 7.2 SEG-002: Idempotencia Atómica En Recepciones De Compra

#### Problema

`PurchaseService` consulta si una recepción ya fue aplicada antes de que inventario reclame atómicamente la llave. Dos solicitudes concurrentes pueden observar que no existe y ambas actualizar `received_quantity`, aunque inventario solo cree un movimiento.

#### Cambios Propuestos

- Reemplazar el resultado simple del caso de uso de inventario por un modelo de aplicación:

```java
public record InventoryReceiptExecution(
    InventoryReceipt receipt,
    InventoryReceiptOutcome outcome
) {}

public enum InventoryReceiptOutcome {
    CREATED,
    REPLAYED
}
```

- Mantener `INSERT ... ON CONFLICT DO NOTHING RETURNING` como reclamo atómico.
- Devolver `CREATED` únicamente al propietario del reclamo.
- Devolver `REPLAYED` al reutilizar una recepción con la misma llave y fingerprint.
- Actualizar `purchase_items.received_quantity`, estado de compra y auditoría solo cuando el resultado sea `CREATED`.
- Bloquear compra y partidas mediante `SELECT ... FOR UPDATE` en orden estable.
- Rechazar la misma llave con payload diferente mediante `409 INVENTORY_RECEIPT_IDEMPOTENCY_CONFLICT`.

#### Pruebas

- Dos solicitudes concurrentes idénticas devuelven la misma recepción.
- Stock, cantidades recibidas y auditoría cambian una sola vez.
- Dos solicitudes con la misma llave y payload distinto generan un éxito y un `409`.
- Un error de lote, caducidad o cantidad revierte toda la recepción.

#### Criterios De Aceptación

- `received_quantity` y stock se incrementan exactamente una vez.
- No se crean auditorías o movimientos duplicados.
- La decisión `CREATED/REPLAYED` proviene del reclamo atómico en PostgreSQL.

### 7.3 SEG-003: Idempotencia Atómica En Ventas

#### Problema

La venta usa una secuencia de consultar y después insertar. Dos reintentos concurrentes pueden superar las consultas iniciales y competir por la restricción única, provocando un error interno para la solicitud perdedora.

#### Cambios Propuestos

- Reclamar la llave mediante una operación atómica:

```sql
INSERT INTO sales.sales_orders (...)
VALUES (...)
ON CONFLICT (idempotency_key) DO NOTHING
RETURNING sales_order_id;
```

- Si no se obtiene una fila:
  - consultar la venta existente;
  - comparar su fingerprint;
  - devolver la existente cuando coincida;
  - responder `409 SALES_ORDER_IDEMPOTENCY_CONFLICT` cuando no coincida.
- Mantener venta, partidas, stock y movimiento de inventario dentro de una sola transacción.
- No traducir la restricción única a `500`.

#### Pruebas

- Dos ventas concurrentes idénticas devuelven el mismo identificador.
- Solo se descuenta stock una vez.
- Dos payloads distintos con la misma llave generan un `409` controlado.
- Ninguna carrera de idempotencia responde `500`.

#### Criterios De Aceptación

- Los reintentos son deterministas.
- La base de datos decide quién obtiene el reclamo.
- No existen ventas, partidas ni movimientos duplicados.

## 8. Fase 2: Límites De Entrada Y Disponibilidad

### 8.1 SEG-004: Limitar Cuerpos Y Colecciones

#### Cambios Propuestos

- Implementar un filtro HTTP previo a MVC que cuente los bytes realmente leídos, incluyendo transferencias sin `Content-Length`.
- Establecer inicialmente un máximo global de 1 MiB para JSON, configurable mediante YAML y variable de entorno.
- Responder `413 REQUEST_PAYLOAD_TOO_LARGE` antes de abrir una transacción.
- Agregar límites de negocio iniciales:

| Operación | Límite propuesto |
|---|---:|
| Partidas por venta | 200 |
| Partidas por compra | 500 |
| Partidas por recepción | 500 |
| Pallets por recepción | 100 |
| Partidas por pallet | 500 |

- Aplicar `@Size`, `@Digits`, longitudes máximas de texto y validación de escala decimal.
- Mantener los límites de Sync actuales; usar la política más restrictiva cuando coincidan filtros.

#### Pruebas

- Cuerpo superior a 1 MiB responde `413` con `ApiResponseDto` y `correlationId`.
- Una lista sobre el máximo responde `400` sin ejecutar repositorios.
- Valores decimales fuera de precisión o escala son rechazados.
- Peticiones válidas en el límite exacto funcionan.

#### Criterios De Aceptación

- Ningún endpoint transaccional procesa colecciones sin límite.
- La configuración puede ajustarse sin recompilar.
- Los errores conservan `code`, `reason`, `path` y `correlationId`.

## 9. Fase 3: Autenticación Y Contraseñas

### 9.1 SEG-005: Evitar Enumeración De Usuarios Por Tiempo

#### Cambios Propuestos

- Configurar un hash BCrypt ficticio válido, generado previamente y sin relación con una contraseña real.
- Cuando el usuario no exista, ejecutar exactamente una comparación contra el hash ficticio.
- Cuando exista, ejecutar una comparación contra su hash real.
- Mantener el mismo código, mensaje y estructura de respuesta para usuario inexistente y contraseña incorrecta.
- Evitar diferencias observables en auditoría síncrona que alteren significativamente la latencia.

#### Pruebas

- El puerto de verificación se invoca exactamente una vez en ambos casos.
- Ambos casos devuelven el mismo error externo.
- Una prueba estadística local confirma que no existe una diferencia sistemática equivalente al costo de BCrypt.

#### Criterios De Aceptación

- La existencia del usuario no cambia el número de verificaciones criptográficas.
- No se revela internamente el motivo en la respuesta HTTP.

### 9.2 SEG-006: Evitar Bloqueo De Cuenta Provocado Por Atacantes

#### Cambios Propuestos

- Sustituir el bloqueo rígido provocado por cinco intentos por retrasos progresivos y limitación combinada:
  - dirección IP;
  - par usuario/IP;
  - cuenta global;
  - dispositivo cuando exista contexto confiable.
- Usar `429 LOGIN_RATE_LIMITED` y `Retry-After` para restricciones temporales.
- Reservar `LOCKED` persistente para administración, riesgo elevado o recuperación controlada.
- Registrar alertas ante ataques distribuidos sobre una cuenta.
- Mantener estructuras en memoria acotadas para la instancia actual; documentar Redis como evolución si se despliegan varias instancias.
- Revisar el uso de `X-Forwarded-For`: solo confiar en encabezados provenientes de un proxy conocido y sanitizado.

#### Pruebas

- Cinco intentos desde una IP no producen un bloqueo persistente explotable.
- El atacante recibe retrasos o `429` antes de afectar indefinidamente al usuario legítimo.
- Intentos distribuidos incrementan señales de riesgo de cuenta.
- Un usuario legítimo puede recuperarse mediante el flujo administrativo definido.

#### Criterios De Aceptación

- Un cliente anónimo no puede mantener bloqueada una cuenta mediante solicitudes de bajo costo.
- Se conserva protección efectiva contra fuerza bruta.

### 9.3 SEG-007: Política Centralizada De Contraseñas

#### Cambios Propuestos

- Crear un puerto de aplicación común, por ejemplo `PasswordPolicyPort`.
- Aplicarlo a:
  - creación de usuarios;
  - cambio de contraseña;
  - bootstrap local;
  - futuras recuperaciones de contraseña.
- Política inicial:
  - mínimo 12 caracteres;
  - máximo 128 caracteres;
  - no modificar ni recortar silenciosamente la contraseña;
  - rechazar contraseñas comunes mediante una lista local versionada sin datos sensibles;
  - rechazar contraseña igual o demasiado similar al username;
  - permitir frases largas y caracteres Unicode válidos.
- Mantener BCrypt con el costo configurado actualmente, salvo que una prueba de rendimiento justifique ajustarlo.

#### Pruebas

- Rechazar contraseñas de un carácter, comunes o basadas en username.
- Aceptar frases largas válidas.
- Confirmar que creación, cambio y bootstrap usan la misma política.
- No escribir contraseñas en logs, auditoría o respuestas.

#### Criterios De Aceptación

- No existe una ruta capaz de guardar una contraseña fuera de la política.
- La política vive en un único componente reutilizable y comprobable.

## 10. Fase 4: Consultas Con Alcance Por Sucursal

### 10.1 SEG-008: Aplicar Sucursal Antes De Ordenar Y Limitar

#### Problema

Ventas y compras obtienen un conjunto limitado globalmente y después filtran por `BranchScope`. Los registros recientes de otras sucursales pueden consumir el límite y ocultar datos autorizados.

#### Cambios Propuestos

- Extender los puertos de consulta para recibir el alcance autorizado como criterio de consulta.
- Para acceso global, omitir el predicado adicional.
- Para acceso limitado, agregar antes de `ORDER BY` y `LIMIT`:

```sql
AND branch_id IN (:authorizedBranchIds)
```

- Si no existen sucursales autorizadas, devolver una lista vacía sin ejecutar una consulta global.
- Aplicar el mismo patrón a ventas, compras y cualquier listado que actualmente filtre después del repositorio.
- Reemplazar progresivamente límites fijos por paginación de cursor usando `created_at` e identificador como desempate estable.

#### Pruebas

- Crear más de 200 registros recientes en sucursal B y registros antiguos en A.
- Confirmar que un usuario de A sigue viendo sus registros.
- Confirmar que no se devuelve información de B.
- Confirmar que `SYSTEM_ADMIN` conserva acceso global.
- Validar paginación estable cuando varios registros comparten fecha.

#### Criterios De Aceptación

- La autorización se aplica en SQL antes del límite.
- Los listados autorizados son completos dentro de su página.
- La capa de aplicación no necesita retirar registros no autorizados después de consultar.

## 11. Migraciones Previstas

Las migraciones se crearán únicamente después de confirmar el esquema actual. La numeración deberá continuar desde la última versión existente.

Posibles cambios:

- Tabla de asignaciones de reembolso.
- Restricciones únicas de asignación por devolución/pago.
- Índices para consultas de reembolsos confirmados.
- Índices compuestos de listados por `branch_id`, `created_at` e identificador.

Reglas:

- No editar migraciones ya aplicadas.
- Usar nombres descriptivos y una sola responsabilidad por migración.
- Probar la cadena completa de migraciones sobre una base efímera.
- No ejecutar `Flyway clean` sobre la base local de trabajo.

## 12. Estrategia De Pruebas

| Nivel | Objetivo |
|---|---|
| Dominio/aplicación | Validar reglas, límites y decisiones `CREATED/REPLAYED` |
| Persistencia PostgreSQL | Validar constraints, locks, `ON CONFLICT` y rollback |
| Concurrencia | Ejecutar dos hilos sincronizados contra PostgreSQL/Testcontainers |
| REST | Validar códigos HTTP, seguridad y contrato `ApiResponseDto` |
| Seguridad | Validar `401`, `403`, `409`, `413`, `429` y `correlationId` |
| Regresión | Confirmar que los flujos actuales siguen funcionando |

Las pruebas concurrentes no deben usar H2. Deben ejecutarse contra PostgreSQL real o Testcontainers para reproducir locks y restricciones correctamente.

## 13. Validación Final

```powershell
cd "C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes\apps\backend"

.\gradlew.bat clean compileJava --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat bootJar --no-daemon
```

```powershell
cd "C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes"

docker compose --profile migrate run --rm flyway migrate
docker compose build backend
docker compose up -d backend
docker compose ps
docker compose logs backend --tail=100
```

Pruebas HTTP finales:

1. Venta con pago mixto y devolución parcial/total.
2. Dos recepciones simultáneas con una llave de idempotencia.
3. Dos ventas simultáneas con una llave de idempotencia.
4. Cuerpo superior al máximo permitido.
5. Login con usuario existente e inexistente.
6. Ataque controlado de intentos fallidos.
7. Creación y cambio de contraseña débil.
8. Listados con dos sucursales y más registros que el límite.

## 14. Commits Recomendados

```text
test(security): reproduce validated backend findings
fix(sales): allocate mixed payment refunds safely
fix(purchasing): make receipt application idempotent
fix(sales): claim order idempotency keys atomically
fix(api): enforce request and collection limits
fix(identity): normalize login verification timing
fix(identity): harden login throttling and password policy
fix(authorization): apply branch scope before pagination
test(security): add concurrency and regression coverage
docs(security): document backend remediation evidence
```

Cada commit debe contener una modificación lógica verificable. No se deben mezclar refactorizaciones generales con correcciones de seguridad.

## 15. Riesgos Del Plan

| ID | Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|---|
| RIE-001 | Cambiar reembolsos afecta ventas históricas | Media | Alto | Probar datos existentes y usar migración compatible |
| RIE-002 | Locks nuevos generan bloqueos cruzados | Media | Alto | Orden estable de bloqueo y pruebas concurrentes |
| RIE-003 | Límites demasiado bajos rechazan operaciones legítimas | Media | Medio | Configuración externa y métricas antes de ajustar |
| RIE-004 | Nueva política impide contraseñas de pruebas actuales | Alta | Bajo | Documentar cuentas locales y rotarlas de forma controlada |
| RIE-005 | Filtrar sucursales en SQL afecta rendimiento | Baja | Medio | Índices, paginación y `EXPLAIN ANALYZE` |
| RIE-006 | Configuración incorrecta de proxy permite evadir límite por IP | Media | Medio | Lista de proxies confiables y sanitización de headers |

## 16. Definición De Terminado

La tanda se considerará terminada cuando:

- Los ocho hallazgos tengan corrección y prueba de regresión.
- Las pruebas concurrentes sean verdes contra PostgreSQL.
- No existan respuestas `500` en carreras de idempotencia conocidas.
- Los reembolsos respeten exactamente los métodos e importes originales.
- Ningún endpoint transaccional acepte colecciones ilimitadas.
- Todas las rutas de contraseña compartan la misma política.
- La consulta por sucursal se realice antes de paginar.
- Compilación, suite completa, migraciones y `bootJar` finalicen correctamente.
- Docker levante un backend saludable.
- Swagger y documentación técnica reflejen los nuevos errores controlados.
- Los cambios estén divididos en commits revisables y no incluyan secretos.

## 17. Pendientes De Definición

- Confirmar límites máximos de partidas con el volumen real esperado de la tienda.
- Confirmar si los reembolsos no efectivos se marcarán como `PENDING` o `CONFIRMED` sin integración con proveedor.
- Definir el mecanismo administrativo de recuperación de cuentas restringidas.
- Confirmar la topología del proxy de producción antes de confiar en encabezados reenviados.
- Ejecutar posteriormente un análisis de dependencias con una base CVE actualizada y una prueba dinámica HTTP.
