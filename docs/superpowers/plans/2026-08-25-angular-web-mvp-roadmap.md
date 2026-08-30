# Plan Maestro de Continuación Angular Web MVP

> **Para la ejecución:** usar `superpowers:executing-plans` y trabajar tarea por tarea en la sesión principal. No crear subagentes ni hilos auxiliares. Cada paso usa casillas `- [ ]` para registrar el avance.

**Objetivo:** completar la aplicación Angular de administración y operación de la tienda sobre los contratos reales del backend, empezando por catálogo y avanzando por dependencias hasta obtener un frontend MVP verificable.

**Arquitectura:** conservar Angular standalone con organización feature-first, rutas lazy, APIs HTTP tipadas y páginas que coordinan Signals, Reactive Forms y RxJS. La autorización visual se deriva de los permisos del JWT, mientras que el backend sigue siendo la autoridad para sucursales, precios, stock, estados y reglas transaccionales.

**Stack tecnológico:** Angular 22.1, TypeScript 6.0, Angular Material 22.1, SCSS, RxJS 7.8, Vitest, `HttpTestingController`, npm 11 y API Spring Boot bajo `/api/v1`.

**Especificación base:** `docs/superpowers/specs/2026-08-25-angular-catalog-design.md`

## Restricciones Globales

- Conservar los cambios locales existentes del árbol de categorías; no reemplazarlos ni descartarlos.
- Mantener Angular Material y SCSS con los modos claro y oscuro existentes.
- No incorporar Tailwind, NgRx ni un servicio CRUD genérico en el MVP.
- Usar componentes standalone, rutas lazy y división por funcionalidad.
- Consumir el backend mediante `API_CONFIG`; no escribir URLs absolutas en features.
- Mantener el contrato `ApiResponse<T>` con `code`, `reason`, `message` y `correlationId`.
- Mostrar acciones únicamente cuando el usuario posea el permiso correspondiente; el backend conserva la autorización definitiva.
- Resolver filtros, orden y paginación en el servidor cuando el endpoint los soporte.
- No persistir contraseñas, tokens ni datos sensibles fuera del almacenamiento de sesión ya definido.
- Ejecutar prueba específica, suite completa y build antes de cerrar cada entrega.
- Usar comandos PowerShell y npm; el usuario realizará los bloques manuales cuando el objetivo sea aprendizaje.
- No usar `git add .`; preparar commits con rutas explícitas para no mezclar trabajo ajeno.

---

| Campo         | Valor                     |
| ------------- | ------------------------- |
| Proyecto      | Tienda de Abarrotes       |
| Código        | TDA-WEB-PLAN-001          |
| Versión       | v1.0                      |
| Estado        | Ejecutado y verificado    |
| Responsable   | Equipo de desarrollo ODCC |
| Fecha         | 2026-08-25                |
| Clasificación | Interno                   |

## Control de Versiones

| Versión | Fecha      | Autor        | Cambio                                                  | Estado                  |
| ------- | ---------- | ------------ | ------------------------------------------------------- | ----------------------- |
| v0.1    | 2026-08-25 | ODCC / Codex | Creación del roadmap maestro Angular                    | Aprobado para ejecución |
| v1.0    | 2026-08-30 | ODCC / Codex | Implementación integral, E2E y cierre del candidato web | Ejecutado y verificado  |

## 1. Estado Actual Verificado

La aplicación web cuenta actualmente con:

- Angular 22 standalone y TypeScript 6.
- Login conectado a `POST /api/v1/auth/login`.
- almacenamiento de sesión, interceptor Bearer y cierre de sesión ante token inválido;
- interceptor de `X-Correlation-ID`;
- guards para sesión, invitados y permisos;
- shell responsive, navegación por permisos y modo oscuro;
- dashboard inicial;
- listado de marcas;
- listado de categorías y seis archivos modificados localmente para alternar lista/árbol;
- placeholders protegidos para inventario, ventas, caja, clientes, reportes e identidad;
- diez archivos de pruebas unitarias;
- plan previo de lectura de catálogo en `docs/superpowers/plans/2026-08-25-angular-catalog-read-flow.md`.

El backend publica 141 operaciones OpenAPI distribuidas entre catálogo, organización, inventario, compras, ventas, pagos, caja, devoluciones, clientes, identidad, reportes, facturación y sincronización. El frontend no debe intentar cubrirlas todas simultáneamente: el avance seguirá dependencias funcionales y entregas verticales comprobables.

### Cambios Locales que Deben Preservarse

```text
apps/web/src/app/features/catalog/categories/data-access/category-api.spec.ts
apps/web/src/app/features/catalog/categories/models/category.model.ts
apps/web/src/app/features/catalog/categories/pages/category-list/category-list.html
apps/web/src/app/features/catalog/categories/pages/category-list/category-list.scss
apps/web/src/app/features/catalog/categories/pages/category-list/category-list.spec.ts
apps/web/src/app/features/catalog/categories/pages/category-list/category-list.ts
```

## 2. Decisiones de Arquitectura

### 2.1 Organización Feature-First

Cada módulo tendrá contratos, acceso a datos, componentes presentacionales y páginas dentro de su propio contexto:

```text
src/app/
├── core/
│   ├── auth/
│   ├── config/
│   ├── context/
│   ├── guards/
│   ├── http/
│   └── theme/
├── layout/
├── shared/
│   ├── components/
│   ├── dialogs/
│   ├── models/
│   └── utilities/
└── features/
    ├── authentication/
    ├── catalog/
    ├── organization/
    ├── inventory/
    ├── purchasing/
    ├── sales/
    ├── cash/
    ├── reports/
    ├── identity/
    ├── billing/
    └── sync/
```

No se crearán carpetas vacías por anticipado. Cada directorio aparecerá cuando la primera entrega funcional lo necesite.

### 2.2 Estado y Comunicación

- Signals: estado local de pantalla, sesión y contexto seleccionado.
- Reactive Forms: búsquedas, filtros y formularios de escritura.
- RxJS: debounce, composición de consultas, cancelación y manejo de respuestas HTTP.
- APIs tipadas: encapsulan rutas, parámetros y extracción de `ApiResponse<T>`.
- Páginas: coordinan navegación, formularios y llamadas; no construyen URLs ni reglas de backend.
- Componentes: reciben entradas y emiten eventos; no conocen endpoints.

### 2.3 Permisos

Cada ruta declara `data.permissions`. Las acciones de crear, editar, cambiar estado, confirmar o cancelar se condicionan con permisos específicos de la sesión. No se duplicarán reglas de negocio como FEFO, precios, stock o acceso por sucursal en Angular.

### 2.4 Manejo de Errores

Los errores deben mostrar el `message` del backend y, cuando exista, `correlationId` como referencia. Los estados mínimos de toda pantalla son:

```text
loading → content | empty | recoverable-error
```

Los `401` cierran sesión; los `403` conducen a `/forbidden`; los `409` muestran el conflicto funcional sin perder los datos capturados por el usuario.

## 3. Orden Recomendado

| Orden | Entrega                               | Dependencia             | Resultado comprobable                                             |
| ----: | ------------------------------------- | ----------------------- | ----------------------------------------------------------------- |
|     0 | Cierre del árbol de categorías        | Base actual             | Categorías alternan lista y árbol con pruebas                     |
|     1 | Catálogo de productos de solo lectura | Entrega 0               | Productos paginados, detalle y presentaciones                     |
|     2 | Escrituras de catálogo                | Entrega 1               | CRUD controlado de marcas, categorías, productos y presentaciones |
|     3 | Contexto operativo                    | Catálogo estable        | Sucursal y almacén activos disponibles en la sesión web           |
|     4 | Inventario y compras                  | Contexto operativo      | Stock, lotes, movimientos, recepciones, proveedores y compras     |
|     5 | Clientes y punto de venta             | Catálogo + inventario   | Venta real con precios del backend y control de stock             |
|     6 | Caja, pagos y devoluciones            | Punto de venta          | Ciclo apertura → venta → pago → devolución → cierre               |
|     7 | Dashboard y reportes                  | Datos operativos        | Indicadores y reportes v1/v2 consumidos por filtros               |
|     8 | Identidad y organización              | Contexto operativo      | Usuarios, roles, permisos y recursos organizacionales             |
|     9 | Facturación y sincronización          | Ventas + administración | Gestión fiscal interna y consola de Sync                          |
|    10 | Cierre de calidad                     | Todas las entregas      | E2E crítico, accesibilidad, documentación y release web           |

### Resultado de ejecución

Las tareas 0 a 10 quedaron implementadas en entregas verticales y commits convencionales. La evidencia final del candidato se conserva en `docs/releases/web-v1.0.0-rc1.md`: 152 pruebas unitarias, cinco flujos E2E, build de producción, auditoría de dependencias, controles de accesibilidad y pruebas reales contra Docker local.

## 4. Plan de Ejecución

### Tarea 0: Cerrar el Árbol de Categorías

**Archivos:** los seis archivos locales enumerados en la sección 1.

**Interfaz producida:** `CategoryApi.tree(status?: CategoryStatus): Observable<CategoryTreeNode[]>` y vista `LIST | TREE`.

- [ ] Revisar `git diff -- apps/web/src/app/features/catalog/categories` y confirmar que solo contiene la vista de árbol.
- [ ] Ejecutar las pruebas específicas:

```powershell
cd "C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes\apps\web"
npm test -- --watch=false --include src/app/features/catalog/categories/**/*.spec.ts
```

- [ ] Ejecutar `npm run build` y corregir únicamente fallos vinculados a categorías.
- [ ] Probar manualmente lista, árbol, filtro de estado, modo oscuro y recarga.
- [ ] Preparar un commit exclusivo:

```powershell
git add src/app/features/catalog/categories/data-access/category-api.spec.ts
git add src/app/features/catalog/categories/models/category.model.ts
git add src/app/features/catalog/categories/pages/category-list/category-list.html
git add src/app/features/catalog/categories/pages/category-list/category-list.scss
git add src/app/features/catalog/categories/pages/category-list/category-list.spec.ts
git add src/app/features/catalog/categories/pages/category-list/category-list.ts
git diff --cached --check
git commit -m "feat(web): add category tree view"
```

**Aceptación:** las pruebas específicas y el build finalizan correctamente; no se modifican login, shell ni backend.

### Tarea 1: Completar Catálogo de Productos de Solo Lectura

**Archivos principales:**

```text
src/app/shared/models/page-response.model.ts
src/app/shared/utilities/api-error-message.ts
src/app/features/catalog/products/models/product.model.ts
src/app/features/catalog/products/data-access/product-api.ts
src/app/features/catalog/products/pages/product-list/*
src/app/features/catalog/products/pages/product-detail/*
src/app/features/catalog/products/components/presentation-table/*
src/app/app.routes.ts
src/app/layout/app-shell/app-shell.ts
```

**Endpoints:**

```text
GET /api/v1/catalog/products
GET /api/v1/catalog/products/{productId}
GET /api/v1/catalog/products/{productId}/presentations
GET /api/v1/catalog/product-presentations/{presentationId}
```

**Permiso de entrada:** `CATALOG_PRODUCT_READ`.

- [ ] Ejecutar primero las tareas pendientes del plan `2026-08-25-angular-catalog-read-flow.md` para contratos compartidos, API paginada, listado y ruta.
- [ ] Crear una prueba HTTP que verifique filtros, paginación y extracción de `data` antes de implementar `ProductApi`.
- [ ] Crear `/catalog/products/:productId` como ruta lazy y probar su permiso.
- [ ] Mostrar datos generales, marca, categoría, políticas y estado del producto.
- [ ] Consultar y presentar SKU, nombre, unidad, impuesto, conversión, contenido neto, stock mínimo y estado de cada presentación.
- [ ] Añadir navegación de tabla a detalle y regreso que conserve filtros mediante query params.
- [ ] Verificar estados de carga, vacío, 404 y error con correlation id.
- [ ] Ejecutar `npm test -- --watch=false` y `npm run build`.
- [ ] Crear commits separados:

```text
refactor(web): share API pagination and error handling
feat(web): add paginated product catalog
feat(web): add product detail and presentations
```

**Aceptación:** un usuario con permiso consulta productos reales y presentaciones; uno sin permiso no ve la ruta ni el menú.

### Tarea 2: Incorporar Escrituras de Catálogo

**Archivos principales:**

```text
features/catalog/brands/components/brand-form-dialog/*
features/catalog/categories/components/category-form-dialog/*
features/catalog/products/pages/product-form/*
features/catalog/products/components/presentation-form-dialog/*
shared/dialogs/confirmation-dialog/*
```

**Endpoints:** CRUD y cambio de estado bajo `/catalog/brands`, `/catalog/categories`, `/catalog/products` y `/catalog/product-presentations`.

**Permisos:** `*_CREATE`, `*_UPDATE` y `*_STATUS` del recurso correspondiente.

- [ ] Diseñar contratos de request separados de los modelos de respuesta.
- [ ] Escribir pruebas de validación para código, nombre, relaciones, políticas de lotes/caducidad, SKU, conversión y mínimos.
- [ ] Implementar altas y ediciones mediante Reactive Forms.
- [ ] Implementar cambios de estado con confirmación explícita; no ofrecer borrado físico.
- [ ] Deshabilitar el envío mientras exista una solicitud activa.
- [ ] Conservar el formulario ante errores `400` o `409` y mostrar el correlation id.
- [ ] Probar cada botón con y sin permiso.
- [ ] Ejecutar suite y build.
- [ ] Commits recomendados:

```text
feat(web): add brand and category maintenance
feat(web): add product maintenance forms
feat(web): add presentation maintenance forms
```

**Aceptación:** catálogo completo administrable según permisos, con validaciones equivalentes al contrato del backend.

### Tarea 3: Crear Contexto Operativo de Sucursal y Almacén

**Archivos principales:**

```text
core/context/operational-context.store.ts
core/context/operational-context.store.spec.ts
features/organization/models/organization.model.ts
features/organization/data-access/organization-api.ts
layout/app-shell/operational-context-selector/*
```

**Endpoints:**

```text
GET /api/v1/organization/branches
GET /api/v1/organization/warehouses
GET /api/v1/organization/cash-registers
```

- [ ] Definir `OperationalContext` con `branchId`, `warehouseId` y `cashRegisterId` opcional.
- [ ] Cargar únicamente recursos visibles para el usuario autenticado.
- [ ] Requerir selección de sucursal antes de entrar a inventario, compras, ventas o caja.
- [ ] Filtrar almacenes y cajas por la sucursal seleccionada.
- [ ] Persistir solo identificadores en `sessionStorage`; revalidarlos al iniciar sesión o recargar.
- [ ] Limpiar el contexto al cerrar sesión.
- [ ] Probar cambio de sucursal, recurso inactivo y selección inválida.
- [ ] Commit recomendado: `feat(web): add branch-scoped operational context`.

**Aceptación:** ninguna pantalla operativa envía identificadores de otra sucursal por conservar estado antiguo.

### Tarea 4: Implementar Inventario y Compras

**Features:**

```text
features/inventory/stock
features/inventory/lots
features/inventory/movements
features/inventory/receipts
features/inventory/advanced
features/purchasing/suppliers
features/purchasing/purchases
```

**Orden interno:** stock → lotes → movimientos → recepción → proveedores → compras → inventario avanzado.

- [ ] Crear un plan hijo para inventario con modelos exactos obtenidos de OpenAPI.
- [ ] Implementar consultas de stock, lotes, pallets y movimientos con filtros del contexto operativo.
- [ ] Implementar recepción simple y con pallets usando una `idempotencyKey` UUID nueva por intento lógico.
- [ ] Implementar proveedores y compras `DRAFT → CONFIRMED → PARTIALLY_RECEIVED | RECEIVED`.
- [ ] Incorporar ajustes, traspasos, conteos, reservas y lotes por caducar detrás de permisos específicos.
- [ ] Nunca calcular stock final en Angular; volver a consultar el backend después de cada escritura.
- [ ] Probar rollback visual: si el backend rechaza una operación, conservar captura y refrescar saldos.
- [ ] Ejecutar pruebas HTTP, pruebas de páginas, suite y build.
- [ ] Commits recomendados:

```text
feat(web): add inventory read views
feat(web): add inventory receipts
feat(web): add suppliers and purchases
feat(web): add advanced inventory operations
```

**Aceptación:** una recepción o compra confirmada aparece en movimientos y modifica el stock consultado sin ajustes manuales en memoria.

### Tarea 5: Implementar Clientes y Punto de Venta

**Features:**

```text
features/sales/customers
features/sales/pos
features/sales/orders
features/sales/components/product-search
features/sales/components/cart
```

**Endpoints:** clientes y `/api/v1/sales/orders`.

- [ ] Crear primero mantenimiento de clientes para poder asociarlos opcionalmente a ventas.
- [ ] Diseñar POS con búsqueda de presentación, carrito, cantidades y resumen calculado por la respuesta del backend.
- [ ] Enviar `unitPrice` únicamente como precio esperado recibido del catálogo; nunca permitir su edición libre.
- [ ] Mantener `discountAmount = 0` mientras el backend no exponga promociones.
- [ ] Generar una idempotency key por venta y reutilizarla al reintentar el mismo payload.
- [ ] Mostrar conflictos `SALES_PRICE_CHANGED`, `PRICE_NOT_CONFIGURED` y `STOCK_INSUFFICIENT` con acción de recarga.
- [ ] Consultar venta confirmada y actualizar stock después de crearla.
- [ ] Probar doble clic, reintento y sesión expirada.
- [ ] Commits recomendados:

```text
feat(web): add customer management
feat(web): add point of sale flow
feat(web): add sales order history
```

**Aceptación:** una venta real queda confirmada una sola vez y refleja el precio, impuesto y total calculados por el servidor.

### Tarea 6: Implementar Caja, Pagos y Devoluciones

**Features:**

```text
features/cash/sessions
features/cash/movements
features/sales/payments
features/sales/returns
```

- [ ] Abrir caja usando `cashRegisterId` del contexto y el usuario autenticado del backend.
- [ ] Mostrar sesión abierta, esperado, contado, diferencia y movimientos.
- [ ] Registrar pagos parciales o completos y movimientos manuales permitidos.
- [ ] Implementar cancelación de pago con confirmación.
- [ ] Crear devolución en borrador, confirmar o cancelar y volver a consultar venta, stock y caja.
- [ ] Bloquear visualmente operaciones después del cierre, sin sustituir la validación del backend.
- [ ] Probar flujo completo: apertura → venta → pago → devolución parcial → cierre.
- [ ] Commits recomendados:

```text
feat(web): add cash session workflow
feat(web): add sales payments
feat(web): add sales returns
```

**Aceptación:** la interfaz refleja los estados devueltos por el backend y no duplica pagos, reembolsos ni cierres.

### Tarea 7: Crear Dashboard y Reportes Operativos

**Features:**

```text
features/dashboard/data-access/dashboard-api.ts
features/dashboard/components/metric-card/*
features/dashboard/components/period-filter/*
features/reports/pages/report-center/*
features/reports/components/*
```

- [ ] Reutilizar los endpoints de ventas, margen, rentabilidad, valorización, caducidad, devoluciones y caja.
- [ ] Mantener filtros `from`, `to`, `branchId`, `warehouseId`, `customerId`, `groupBy` y `limit` tipados.
- [ ] Implementar primero tarjetas y tablas; agregar gráficas solo cuando la serie temporal ya esté probada.
- [ ] No sumar ni netear resultados nuevamente en el navegador si el endpoint entrega el valor final.
- [ ] Probar venta cancelada, devolución confirmada, rango inválido y ausencia de datos.
- [ ] Commit recomendado: `feat(web): add operational dashboard and reports`.

**Aceptación:** los indicadores cambian con los filtros y coinciden con las respuestas directas del backend.

### Tarea 8: Completar Identidad y Organización Administrativa

**Features:**

```text
features/identity/users
features/identity/roles
features/identity/permissions
features/organization/branches
features/organization/warehouses
features/organization/cash-registers
features/organization/devices
```

- [ ] Implementar usuarios, cambio de estado, contraseña, roles y sucursales.
- [ ] Implementar roles, estado y asignación de permisos.
- [ ] Proteger `SYSTEM_ADMIN` en la experiencia visual y mostrar el error funcional si el backend rechaza el cambio.
- [ ] Implementar CRUD controlado de sucursales, almacenes, cajas y dispositivos.
- [ ] Invalidar o recargar la sesión cuando cambien permisos del usuario actual.
- [ ] Probar usuarios sin permisos administrativos y recursos inactivos.
- [ ] Commits recomendados:

```text
feat(web): add user and role administration
feat(web): add organization administration
```

**Aceptación:** un administrador configura accesos sin SQL y un usuario nuevo ve únicamente módulos autorizados.

### Tarea 9: Incorporar Facturación Interna y Consola Sync

**Features:**

```text
features/billing/issuer-profiles
features/billing/fiscal-profiles
features/billing/fiscal-documents
features/sync/inbox
features/sync/outbox
features/sync/checkpoints
features/sync/conflicts
```

- [ ] Administrar perfiles fiscales emisores y receptores con validación de RFC y código postal.
- [ ] Clasificar productos y unidades con códigos SAT usando los endpoints del catálogo.
- [ ] Crear documento fiscal `DRAFT`, revisar snapshots y pasarlo a `READY`.
- [ ] No presentar timbrado CFDI ni PAC como funcionalidad disponible.
- [ ] Construir consola técnica de Sync para consultar checkpoint, outbox y conflictos.
- [ ] Permitir resoluciones únicamente según las opciones aceptadas por el backend.
- [ ] Probar idempotencia, secuencia, conflictos y acceso por dispositivo/sucursal.
- [ ] Commits recomendados:

```text
feat(web): add internal fiscal management
feat(web): add synchronization operations console
```

**Aceptación:** facturación interna y Sync son administrables sin exponer tokens, payloads sensibles ni datos fiscales innecesarios.

### Tarea 10: Cierre de Calidad y Release Web MVP

**Archivos:**

```text
apps/web/README.md
apps/web/package.json
apps/web/angular.json
apps/web/e2e/
docs/operations/
docs/releases/
```

- [x] Actualizar README con Node compatible, instalación, proxy, pruebas, build y variables no sensibles.
- [x] Agregar Playwright únicamente para los flujos críticos: login, catálogo, venta/pago y autorización 403.
- [x] Ejecutar auditoría de accesibilidad por teclado, labels, foco, contraste y tablas responsivas.
- [x] Validar que ningún bundle contenga secretos o URLs privadas.
- [ ] Ejecutar:

```powershell
cd "C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes\apps\web"
npm ci
npm test -- --watch=false
npm run build
npm audit --omit=dev
```

- [x] Levantar backend Docker y ejecutar E2E contra `http://localhost:4200` mediante proxy local.
- [x] Documentar evidencia y versión en `docs/releases`.
- [x] Commit recomendado: `chore(web): prepare Angular MVP release candidate`.

**Aceptación:** suite unitaria, build y E2E críticos terminan correctamente; no hay errores de consola ni secretos versionados.

## 5. Estrategia de Pruebas

| Nivel               | Herramienta             | Responsabilidad                                            |
| ------------------- | ----------------------- | ---------------------------------------------------------- |
| Utilidades y stores | Vitest                  | Estado, permisos, contexto e idempotencia local            |
| API HTTP            | `HttpTestingController` | Método, ruta, parámetros, payload y extracción de envelope |
| Componentes/páginas | TestBed + Vitest        | Carga, vacío, error, permisos y eventos                    |
| Rutas               | Vitest                  | Lazy loading, guards y `data.permissions`                  |
| Build               | Angular CLI             | Templates, tipos, estilos y presupuestos                   |
| E2E crítico         | Playwright              | Login, catálogo, venta/pago y acceso denegado              |
| Manual              | Navegador               | Modo oscuro, responsive, teclado y datos reales            |

Cada bug corregido debe incorporar una prueba que reproduzca el fallo antes de modificar la implementación.

## 6. Riesgos y Mitigaciones

| Código  | Riesgo                                             | Impacto | Mitigación                                                                    |
| ------- | -------------------------------------------------- | ------: | ----------------------------------------------------------------------------- |
| RIE-001 | Intentar implementar todos los módulos en paralelo |    Alto | Seguir el orden y cerrar una entrega vertical antes de abrir la siguiente     |
| RIE-002 | Sobrescribir cambios locales de categorías         |    Alto | Commit exclusivo de Tarea 0 y staging por ruta                                |
| RIE-003 | Duplicar reglas del backend                        |    Alto | Usar respuestas del servidor para precios, stock, estados y totales           |
| RIE-004 | Permisos visuales inconsistentes                   |    Alto | Probar ruta, menú y acción para cada permiso                                  |
| RIE-005 | Formularios acoplados a responses                  |   Medio | Separar modelos de request y response                                         |
| RIE-006 | Componentes gigantes de POS/inventario             |    Alto | Dividir búsqueda, carrito, resumen y formularios en unidades presentacionales |
| RIE-007 | Dependencias frontend innecesarias                 |   Medio | Angular Material + SCSS; justificar cualquier paquete nuevo                   |
| RIE-008 | Reintentos que duplican operaciones                |    Alto | Mantener idempotency keys por intento lógico y deshabilitar doble envío       |
| RIE-009 | Contexto de sucursal obsoleto                      |    Alto | Revalidar identificadores al iniciar y limpiar al cerrar sesión               |
| RIE-010 | Cobertura superficial                              |   Medio | HTTP specs, page specs, build y E2E crítico antes del release                 |

## 7. Definición de Terminado por Entrega

Una entrega se considera terminada únicamente cuando:

- [ ] la funcionalidad cumple su criterio de aceptación;
- [ ] rutas y acciones respetan permisos;
- [ ] loading, vacío, error y éxito son visibles;
- [ ] errores muestran correlation id cuando está presente;
- [ ] prueba específica y suite completa pasan;
- [ ] `npm run build` finaliza correctamente;
- [ ] modo claro/oscuro y escritorio/tablet son utilizables;
- [ ] no se agregaron secretos ni artefactos generados;
- [ ] el diff contiene solo el alcance de la entrega;
- [ ] existe un commit convencional y reversible.

## 8. Protocolo de Aprendizaje Manual

Para cada tarea se seguirá este ciclo:

1. explicar el propósito del bloque;
2. entregar comandos PowerShell para crear archivos;
3. entregar primero la prueba;
4. ejecutar y observar el fallo esperado;
5. entregar la implementación mínima;
6. ejecutar prueba específica;
7. ejecutar suite y build;
8. revisar la pantalla con datos reales;
9. explicar el flujo de datos y los errores comunes;
10. preparar el commit con archivos explícitos.

## 9. Próxima Acción

El roadmap del MVP web está cerrado. La siguiente acción es publicar la rama, ejecutar los checks remotos y realizar aceptación funcional con perfiles de administrador, cajero y almacén antes de etiquetar `web-v1.0.0-rc1`.

## 10. Fuera de Alcance del MVP Web

- aplicación móvil;
- ventas y pagos offline desde Angular;
- timbrado CFDI con PAC;
- promociones y descuentos manuales no soportados por backend;
- reemplazo del diseño Angular Material por Tailwind;
- NgRx sin una necesidad de estado global demostrada;
- exportaciones PDF/Excel no expuestas por el backend;
- reglas de stock, FEFO, precios o autorización calculadas en el navegador.
