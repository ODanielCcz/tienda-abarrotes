# Campaña De Rendimiento

Este directorio contiene herramientas exclusivas para el proyecto Docker efímero `tienda-perf`. No ejecutes estos escenarios contra producción ni contra una base con información real.

## Preparación

```powershell
.\scripts\perf-environment-up.ps1
```

El script genera secretos aleatorios bajo
`%LOCALAPPDATA%\TiendaAbarrotes\performance\perf.env`, fuera del repositorio y de OneDrive. Luego construye backend y Angular, migra una base nueva y expone únicamente en `127.0.0.1`:

- backend: `http://localhost:58081`;
- Angular: `http://localhost:54201`.

## Cierre

```powershell
.\scripts\perf-environment-down.ps1
```

El cierre elimina contenedores, red, volúmenes y credenciales efímeras. Los artefactos sanitizados permanecen bajo `artifacts/quality` para elaborar el informe.

Los resultados crudos se guardan en
`%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts`. Solo se copiarán al repositorio métricas y evidencias previamente sanitizadas. El contenedor k6 recibe exclusivamente sus credenciales de prueba; no recibe los secretos de PostgreSQL, Redis ni de firma JWT.

Para reutilizar imágenes ya construidas, sin reutilizar la base:

```powershell
.\scripts\perf-environment-up.ps1 -SkipBuild
```

La opción `-ReuseExisting` debe usarse únicamente cuando se quiera conservar deliberadamente el volumen efímero entre pasos de una misma campaña. La ejecución predeterminada comienza desde cero.
