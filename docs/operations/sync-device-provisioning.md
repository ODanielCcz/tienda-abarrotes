# Provisionamiento Seguro De Dispositivos Sync

Sync v1 acepta únicamente dispositivos `MOBILE_EMPLOYEE` activos. Cada dispositivo debe tener un único usuario responsable en `sync.device_user_bindings`; la API rechaza una operación cuando el usuario autenticado no coincide con ese vínculo.

## Precondiciones

- La migración `V034__sync_device_user_bindings.sql` está aplicada.
- El dispositivo, su sucursal y el usuario están `ACTIVE`.
- El usuario tiene acceso `ACTIVE` a la sucursal del dispositivo o conserva el rol activo `SYSTEM_ADMIN`.
- El CSV contiene únicamente UUID reales, una fila por dispositivo y no se versiona.

## Preparar El Archivo Local

```powershell
Set-Location 'C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes'
New-Item -ItemType Directory -Force -Path '.\data\local-only\sync' | Out-Null
Copy-Item '.\docs\operations\examples\sync-device-bindings.example.csv' '.\data\local-only\sync\device-user-bindings.csv'
```

Edita el archivo local y reemplaza los UUID de ejemplo. Nunca incluyas usuarios, dispositivos ni credenciales reales en documentos o commits.

## Revisar Antes De Escribir

```powershell
.\scripts\provision-sync-device-bindings.ps1 `
  -MappingPath '.\data\local-only\sync\device-user-bindings.csv' `
  -WhatIf
```

El modo `-WhatIf` valida cada fila sin insertar ni reasignar vínculos.

## Aplicar

```powershell
.\scripts\provision-sync-device-bindings.ps1 `
  -MappingPath '.\data\local-only\sync\device-user-bindings.csv'
```

El script es idempotente: conserva una asignación ya existente para el mismo usuario. No reasigna un dispositivo perteneciente a otro usuario; esa operación requiere una revisión administrativa explícita y una modificación controlada de la asignación vigente.

## Comprobación

Usa la colección Postman con el token del usuario vinculado y configura `device_id` con su dispositivo. Una llamada a `GET /api/v1/sync/devices/{{device_id}}/checkpoint` debe responder correctamente. El mismo request, autenticado con otro usuario, debe responder `409` y no exponer datos del dispositivo.

## Recuperación

Si un usuario cambia de dispositivo, desactiva o retira primero el dispositivo anterior desde Organización. Después revisa la nueva asignación, cambia el vínculo mediante un procedimiento administrativo auditado y reinicia la secuencia local del dispositivo nuevo en lugar de reutilizar la del anterior.
