# Operación local del Angular Web MVP

| Campo         | Valor                             |
| ------------- | --------------------------------- |
| Documento     | Guía de operación Angular Web MVP |
| Versión       | 1.0                               |
| Estado        | Vigente para RC1                  |
| Fecha         | 2026-08-30                        |
| Clasificación | Interno                           |

## Propósito

Esta guía permite levantar y validar la aplicación Angular contra el backend local sin versionar secretos ni depender de una instalación global de Angular CLI.

## 1. Levantar el backend

Desde la raíz del repositorio:

```powershell
docker compose up -d database
docker compose --profile migrate run --rm flyway migrate

Set-Location .\apps\backend
.\gradlew.bat bootJar --no-daemon

Set-Location ..\..
docker compose build backend
docker compose up -d backend
docker compose ps
```

El servicio `backend` debe aparecer como `healthy`. Swagger queda en `http://localhost:8080/swagger-ui/index.html`.

## 2. Levantar Angular

```powershell
Set-Location .\apps\web
npm ci
npm start
```

Abre `http://localhost:4200`. El proxy de desarrollo conserva `/api/v1` como única base de llamadas desde las funcionalidades.

## 3. Validación automatizada

```powershell
npm test -- --watch=false
npm run build
npm audit --omit=dev
```

Instala una vez Chromium para Playwright:

```powershell
npm run e2e:install
```

Ejecuta los flujos autenticados sin guardar la contraseña:

```powershell
$env:E2E_USERNAME = '<usuario-local-con-permisos>'
$env:E2E_PASSWORD = '<contraseña-local>'
npm run e2e
Remove-Item Env:E2E_USERNAME, Env:E2E_PASSWORD
```

Los E2E escriben una recepción de inventario únicamente cuando hace falta preparar stock y crean una venta pagada. Usa una base local o efímera.

Los flujos también fallan si Angular emite errores en la consola o si ocurre una excepción no controlada en la página.

## 4. Validación manual mínima

1. Inicia sesión y verifica que el menú corresponde a los permisos del usuario.
2. Selecciona sucursal y almacén; cambia de sucursal y confirma que almacén/caja se limpian.
3. Alterna modo claro y oscuro.
4. Recorre login, menú, filtros, tablas y diálogos usando `Tab`, `Shift+Tab`, `Enter`, `Espacio` y `Escape`.
5. Reduce el viewport a tamaño tablet y confirma desplazamiento horizontal en tablas sin cortar acciones.
6. Provoca un error funcional controlado y verifica que muestre el correlation id.
7. Confirma que una venta usa el precio recibido del backend y que un pago actualiza el estado a `PAID`.

## 5. Diagnóstico

### Login funciona por API pero falla en navegador

Confirma que Angular se abra con `http://localhost:4200`. Usar `127.0.0.1` cambia el origen y puede ser rechazado si `CORS_ALLOWED_ORIGINS` solo declara `localhost`.

### El POS no muestra productos

Una presentación debe cumplir simultáneamente:

- producto y presentación activos;
- stock disponible en el almacén seleccionado;
- precio GENERAL vigente para la sucursal o global.

Consulta stock y precios desde Swagger antes de modificar datos manualmente.

### Docker usa código backend anterior

El Dockerfile copia el JAR ya construido. Regenera primero el artefacto:

```powershell
Set-Location .\apps\backend
.\gradlew.bat bootJar --no-daemon
Set-Location ..\..
docker compose build backend
docker compose up -d --force-recreate backend
```

### Evidencia de Playwright

Ante un fallo, Playwright conserva captura, video y traza bajo `apps/web/test-results`. Estos artefactos están ignorados por Git.

## 6. Cierre

```powershell
docker compose down
Remove-Item Env:E2E_USERNAME, Env:E2E_PASSWORD -ErrorAction SilentlyContinue
```
