# Campaña de seguridad local

Objetivo exclusivo: proyecto Docker `tienda-perf`, con fixtures sintéticos y puertos
publicados sólo en loopback. No ejecutar estos casos contra otros entornos.

Desde la raíz del repositorio, con el laboratorio iniciado:

```powershell
.\scripts\perf-environment-up.ps1 -SkipBuild
.\scripts\run-security-assessment.ps1
.\scripts\perf-environment-down.ps1
```

El runner fija los destinos internos `web-performance` y `backend`, comprueba el
proyecto Compose y guarda los JSON crudos fuera de OneDrive en
`%LOCALAPPDATA%\TiendaAbarrotes\performance\artifacts`. Las credenciales y el JWT
se cargan de `perf.env` sólo en memoria; nunca se escriben en Git ni en el
informe. Los dos planes ZAP son pasivos. El autenticado cubre sólo cuatro GET
permitidos y se ejecutó por separado en una corrida con autorización expresa;
el runner general no entrega JWT al contenedor. Cualquier nueva ejecución
autenticada requiere autorización expresa. El runner tampoco ejecuta el
escáner activo general de ZAP sobre operaciones de escritura.

`cases/authorization-matrix.ps1` verifica 401/403, JWT manipulado y control
horizontal con una segunda sucursal sintética. `cases/security-regression.ps1`
añade búsqueda SQL/XSS, CORS, límite de tamaño y profundidad Sync. Los casos de
carrera con bloqueos deliberados de PostgreSQL y la caída de Redis requieren un
laboratorio desechable y se describen con evidencia sanitizada en el informe.

Informe: `docs/reports/quality/2026-08-30-fullstack-stress-security-analysis.md`.
