# Diseño del catálogo Angular

Fecha: 2026-08-25
Estado: aprobado para planificación
Alcance: `apps/web`

## Objetivo

Completar el catálogo de la aplicación web antes de implementar inventario, caja y punto de venta. Esta etapa debe consolidar los patrones que reutilizarán los módulos posteriores: autenticación, permisos, navegación lazy, consumo HTTP, formularios, tablas, paginación, manejo de errores y pruebas.

## Estado actual

La aplicación ya cuenta con:

- Angular standalone y rutas lazy.
- Login, almacenamiento de sesión, JWT, interceptores y guards.
- Navegación filtrada por permisos.
- Angular Material, SCSS y modo oscuro.
- Dashboard.
- Listados de marcas y categorías.
- Cambios locales pendientes para alternar categorías entre lista y árbol.
- Rutas placeholder para inventario, ventas, caja, clientes, reportes e identidad.

Los cambios locales del árbol de categorías se conservarán y validarán antes de iniciar productos.

## Alcance funcional

La entrega se divide en este orden:

1. Cerrar y validar la vista de árbol de categorías.
2. Implementar el listado paginado de productos.
3. Consultar el detalle de un producto.
4. Mostrar las presentaciones asociadas al producto.
5. Crear y actualizar productos.
6. Crear y actualizar presentaciones.
7. Completar altas, actualizaciones y cambios de estado de marcas y categorías.
8. Ocultar o bloquear rutas y acciones según los permisos del usuario.

### Listado de productos

La ruta principal será:

```text
/catalog/products
```

Permitirá:

- buscar por nombre, SKU o código;
- filtrar por marca, categoría y estado;
- navegar con paginación controlada por el servidor;
- consultar el detalle de un producto;
- crear un producto cuando exista `CATALOG_PRODUCT_CREATE`;
- editarlo cuando exista `CATALOG_PRODUCT_UPDATE`;
- cambiar su estado cuando exista `CATALOG_PRODUCT_STATUS`.

### Detalle y presentaciones

La consulta de detalle mostrará la información comercial, políticas de inventario, marca, categoría y presentaciones vendibles. Las presentaciones se administrarán desde el contexto del producto para conservar la relación padre-hijo y evitar seleccionar manualmente un producto equivocado.

## Arquitectura frontend

El catálogo conservará una estructura feature-first:

```text
features/catalog/
├── brands/
│   ├── data-access/
│   ├── models/
│   ├── components/
│   └── pages/
├── categories/
│   ├── data-access/
│   ├── models/
│   ├── components/
│   └── pages/
└── products/
    ├── data-access/
    ├── models/
    ├── components/
    └── pages/
        ├── product-list/
        ├── product-detail/
        └── product-form/
```

Responsabilidades:

- `models`: contratos TypeScript consumidos por la funcionalidad.
- `data-access`: URLs, parámetros HTTP y extracción del `ApiResponse`.
- `components`: piezas presentacionales reutilizables dentro del catálogo.
- `pages`: coordinación de estado, navegación y casos de interacción.

No se crearán servicios genéricos de CRUD. Cada recurso conservará un API tipado para que sus contratos y operaciones sean explícitos.

## Estado y flujo de datos

- Signals manejarán estado local de pantalla: carga, filtros, resultados y errores.
- Reactive Forms manejará filtros y formularios de edición.
- RxJS manejará solicitudes HTTP, debounce de búsqueda y finalización de carga.
- No se incorporará NgRx en esta fase porque no existe estado global complejo que lo justifique.
- Los filtros, página y orden se enviarán al backend; la web no paginará conjuntos completos en memoria.
- Al terminar una escritura se actualizará el recurso afectado o se volverá a consultar la página actual.

Flujo esperado:

```text
Página → API tipada → interceptor JWT/correlation ID → backend
       ← ApiResponseDto ← respuesta validada
```

## Navegación y permisos

Las rutas se cargarán de forma lazy. El guard de permisos protegerá la navegación y la interfaz ocultará acciones no autorizadas. Ocultar un botón no sustituye la autorización del backend; solamente mejora la experiencia.

La navegación de Catálogo incluirá:

```text
Catálogo
├── Marcas
├── Categorías
└── Productos
```

La ruta de productos requerirá `CATALOG_PRODUCT_READ`. Los botones de escritura usarán los permisos específicos de creación, actualización y estado.

## Interfaz y estilos

- Se mantendrá Angular Material con SCSS.
- No se añadirá Tailwind en esta etapa.
- Los componentes utilizarán tokens del tema para conservar modo claro y oscuro.
- Las tablas tendrán estado de carga, estado vacío y error visible.
- Las pantallas serán utilizables en escritorio y tablet; en anchos reducidos los filtros se apilarán y las tablas permitirán desplazamiento horizontal.
- Los formularios mostrarán validaciones antes de enviar y errores funcionales devueltos por el backend.

## Manejo de errores

- Los APIs extraerán `data` de respuestas exitosas sin perder el contrato tipado.
- Los errores mostrarán el `message` funcional del backend cuando esté disponible.
- El `correlationId` se conservará y se mostrará como referencia cuando el error requiera soporte.
- Un `401` seguirá cerrando la sesión mediante la infraestructura existente.
- Un `403` conducirá a la pantalla de acceso restringido.
- Los errores no controlados tendrán un mensaje comprensible y no expondrán detalles internos.

## Pruebas

Cada funcionalidad incluirá:

- pruebas de APIs con `HttpTestingController`;
- pruebas de páginas para carga, filtros, errores y permisos;
- pruebas de formularios para validación y payload enviado;
- pruebas de rutas y guards cuando se agreguen permisos nuevos a la navegación;
- ejecución completa de `npm test -- --watch=false`;
- compilación de producción con `npm run build`.

## Método de aprendizaje

La implementación se entregará manualmente en bloques pequeños:

1. comandos PowerShell para crear carpetas y archivos;
2. código de una capa a la vez;
3. explicación de responsabilidad y dependencias;
4. prueba específica del bloque;
5. compilación ejecutada por el usuario;
6. revisión del resultado antes de avanzar.

No se entregará todo el módulo en un único bloque de código.

## Fuera de alcance

Esta etapa no implementará:

- inventario, compras, caja o ventas;
- punto de venta;
- reportes o facturación;
- aplicación móvil;
- NgRx;
- Tailwind;
- cambios en los contratos del backend salvo que se detecte un bloqueo real.

## Criterios de aceptación

- El árbol de categorías queda probado y versionado.
- Marcas, categorías, productos y presentaciones pueden consultarse y administrarse de acuerdo con los permisos.
- El listado de productos usa filtros y paginación del backend.
- Las rutas se cargan lazy y están protegidas.
- La interfaz funciona en modo claro y oscuro.
- Errores y `correlationId` se presentan de forma consistente.
- Todas las pruebas y el build de Angular finalizan correctamente.
- No se rompe el login, dashboard ni las rutas existentes.
