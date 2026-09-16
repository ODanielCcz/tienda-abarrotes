# Medición Angular de producción

Estado: standalone, Lighthouse y combinado ejecutados con hallazgos documentados.

Se usa exclusivamente `http://localhost:54201`, servido por Nginx desde el build
de producción. No se inicia `ng serve`. Credenciales y evidencia permanecen en
`%LOCALAPPDATA%\TiendaAbarrotes\performance`, fuera del repositorio y de OneDrive.

## Escenarios

- Playwright, un worker sin reintentos: escritorio 1440×900, tablet 820×1180 y
  móvil 390×844. Son tamaños/emulación de Chromium, no dispositivos físicos.
- Dos ciclos de calentamiento y después 50 ciclos SPA de dashboard, catálogo,
  filtros, paginación, detalle y tema. Sin recargar documento entre ciclos.
- Recorrido separado de 50 aperturas/cancelaciones de diálogos reales con el
  administrador efímero. Se comprueba que no salgan escrituras de catálogo.
  El operador de carga conserva sus permisos restringidos.
- Se conserva una prueba independiente de disponibilidad del contexto en el
  viewport objetivo. El CSS actual lo oculta por debajo de 1050 px. Para medir
  el resto del flujo, sólo la preparación del contexto usa ancho de escritorio;
  después se restaura tablet/móvil antes del calentamiento y los ciclos. Esta
  adaptación no hace pasar la prueba de disponibilidad ni corrige la interfaz.
- El selector de tamaño del paginador se acciona con teclado (Enter) sobre su
  combobox; no con un clic forzado que atraviese el área táctil de Material.
- Heap después de GC explícito, DOM, listeners, tareas largas acumuladas y errores
  por punto de muestreo. GC es instrumentación de laboratorio, no comportamiento
  habitual de un usuario.
- Error 503 inyectado exclusivamente en la ruta del navegador, identificado como
  `browser-route-fault-injection`; comprueba presentación de correlationId, no
  representa una caída real del backend.
- Modo combinado: 25 VU constantes, confirmados cada cinco segundos por la API de
  control de k6 vinculada sólo a loopback. Se detiene al finalizar el navegador.

## Lighthouse

Tres ejecuciones independientes por login, dashboard y productos, usando mediana.
`lighthouserc.json` conserva configuración y criterios. El ejecutor utiliza la API
Node de Lighthouse sobre la misma página Puppeteer autenticada: la aplicación usa
`sessionStorage`, que no se transmite automáticamente a una pestaña distinta.
Se verifica la ruta final para no medir un redirect a login como si fuera dashboard.
Se limpia la caché HTTP antes de cada auditoría; se conserva la sesión. Esta es una
adaptación explícita del ejecutor Lighthouse CI previsto, no una ejecución de su CLI.

`PERF_LIGHTHOUSE_CHROME_PATH` permite seleccionar un ejecutable Chromium local.
En esta máquina, el Chrome completo de Playwright falla con `spawn UNKNOWN`,
mientras Chromium Headless Shell 151.0.7922.34 inicia con Playwright y Puppeteer.
Se usa este último, registrando ruta y user agent en la evidencia. No se debe
interpretar como una medición sobre un navegador móvil físico.

Referencias primarias:
- [Lighthouse con autenticación](https://github.com/GoogleChrome/lighthouse/blob/main/docs/recipes/auth/README.md)
- [Control local de k6](https://grafana.com/docs/k6/latest/reference/k6-rest-api/)

## Ejecución

No ejecutar mientras corre una campaña independiente de carga backend.
Cada comando del runner reinicia **sólo el entorno desechable tienda-perf**.
Requiere build de producción y dependencias instaladas previamente.

```powershell
# Desde la raíz del repositorio:
node tests/performance/web/test-web-metrics.cjs
.\tests\performance\scripts\run-web-performance.ps1 -Mode standalone -Quick
.\tests\performance\scripts\run-web-performance.ps1 -Mode standalone
# Sólo si el Chrome completo local no inicia; ajustar a la versión instalada:
$env:PERF_LIGHTHOUSE_CHROME_PATH = Join-Path $env:LOCALAPPDATA 'ms-playwright\chromium_headless_shell-1234\chrome-headless-shell-win64\chrome-headless-shell.exe'
.\tests\performance\scripts\run-web-performance.ps1 -Mode lighthouse
.\tests\performance\scripts\run-web-performance.ps1 -Mode combined
```

`Quick` sólo reduce Playwright a dos ciclos; nunca cuenta como evidencia de 50
ciclos. No reduce Lighthouse. Las pruebas funcionales fallidas conservan muestras
parciales; un recorrido incompleto no cumple el criterio de memoria. Las pruebas
no fuerzan clics ni ocultan desbordamientos para obtener un resultado positivo.

Umbrales: navegación p95 ≤1000 ms, heap ≤20% sobre baseline después de GC,
consola/excepciones/HTTP inesperados cero, LCP ≤2500 ms, CLS ≤0,1 y puntuaciones
Lighthouse de rendimiento ≥85, accesibilidad ≥95, buenas prácticas ≥90.

La preparación de estos archivos durante el soak no cambia el JAR ni el `dist`
servido; no se ejecutan navegador, instalación de paquetes o build hasta que termine.
