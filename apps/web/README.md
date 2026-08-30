# Tienda Web

Aplicación Angular de administración y operación para Tienda de Abarrotes. Consume el backend mediante el proxy local `/api` y conserva en el servidor la autoridad sobre permisos, sucursales, precios, stock, impuestos, estados e idempotencia.

## Requisitos

- Node.js `22.22.2`, `24.15.0` o una versión compatible posterior indicada en `package.json`.
- npm `11.17.0`.
- Backend disponible en `http://localhost:8080`.
- Chromium de Playwright para las pruebas E2E.

Comprueba el entorno:

```powershell
node --version
npm --version
```

## Instalación reproducible

Desde `apps/web`:

```powershell
npm ci
npm run e2e:install
```

`npm ci` utiliza `package-lock.json`; no es necesario instalar Angular CLI globalmente.

## Desarrollo local

Levanta primero el backend. Después:

```powershell
npm start
```

Abre `http://localhost:4200`. El archivo `proxy.conf.json` reenvía `/api/**` hacia `http://localhost:8080`. Usa `localhost`, no `127.0.0.1`, para coincidir con el origen CORS local documentado.

La aplicación no requiere secretos de frontend. No agregues JWT, contraseñas, credenciales de base de datos ni URLs privadas dentro de `src`, `public` o archivos de configuración versionados.

## Comandos de calidad

```powershell
npm test -- --watch=false
npm run build
npm audit --omit=dev
```

El build de producción se genera en `dist/tienda-web`.

## Pruebas E2E

Los E2E cubren:

- login y catálogo;
- venta con precio resuelto por el backend y pago;
- acceso restringido por permisos;
- auditoría WCAG A/AA en login, dashboard y productos.

Define las credenciales solo en la sesión de PowerShell:

```powershell
$env:E2E_USERNAME = '<usuario-local-con-permisos>'
$env:E2E_PASSWORD = '<contraseña-local>'
npm run e2e
Remove-Item Env:E2E_USERNAME, Env:E2E_PASSWORD
```

También puedes usar:

```powershell
npm run e2e:ui
```

La prueba de venta prepara su precondición mediante APIs públicas si no existe stock para una presentación con precio vigente. Por ello debe ejecutarse únicamente contra una base local o efímera, nunca contra producción.

Si las variables no existen, los flujos autenticados se omiten y la prueba determinista del guard de permisos permanece ejecutable.

## Estructura

```text
src/app/
├── core/       sesión, configuración, contexto, guards e interceptores
├── layout/     shell y selector operativo
├── shared/     contratos, utilidades y diálogos reutilizables
└── features/   módulos funcionales con modelos, APIs, componentes y páginas
```

La aplicación usa componentes standalone, rutas lazy, Signals, Reactive Forms, RxJS, Angular Material y SCSS. No duplica reglas críticas del backend.

## Seguridad operativa

- El token se conserva en `sessionStorage` y se elimina al cerrar sesión o recibir una revocación.
- Las acciones y rutas se muestran según permisos del JWT; el backend vuelve a autorizar cada petición.
- `X-Correlation-ID` se propaga y se muestra como referencia en errores.
- El contexto de sucursal, almacén y caja se revalida con recursos activos del servidor.
- No se admiten precios ni descuentos libres desde el POS.

Consulta la guía completa en [`../../docs/operations/angular-web-mvp.md`](../../docs/operations/angular-web-mvp.md).
