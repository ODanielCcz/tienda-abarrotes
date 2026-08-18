# Operación Del Backend MVP

## Preparación

```powershell
Set-Location 'C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes'
Copy-Item .env.example .env
```

Reemplaza contraseñas y genera `JWT_SECRET_BASE64`:

```powershell
[Convert]::ToBase64String(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)
```

## Despliegue Local

```powershell
docker compose up -d database
docker compose --profile migrate run --rm flyway migrate
docker compose build backend
docker compose up -d backend
docker compose ps
```

Swagger: `http://localhost:8080/swagger-ui/index.html`

Health: `http://localhost:8080/actuator/health`

## Validación Antes De Desplegar

```powershell
Set-Location .\apps\backend
.\gradlew.bat compileJava --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat bootJar --no-daemon
```

Para validar un candidato contra una base vacía y aislada, usa la guía [rc1-validation.md](rc1-validation.md). No ejecutes `Flyway clean` ni elimines los volúmenes de desarrollo para este fin.

## Sync Offline V1

Antes de probar Inbox, Outbox, checkpoints o conflictos, cada dispositivo `MOBILE_EMPLOYEE` debe tener un propietario explícito en `sync.device_user_bindings`. Sigue [sync-device-provisioning.md](sync-device-provisioning.md); un usuario distinto al propietario no puede sincronizar usando el UUID de ese dispositivo.

Los requests Sync tienen límites de 256 KiB, profundidad 20, 1,000 nodos y 300 operaciones por minuto por dispositivo. Los rechazos usan el contrato estándar y pueden devolver `413`, `400` o `429`.

## Recuperación Ante Errores

| Falla | Acción |
|---|---|
| Docker no responde | Iniciar Docker Desktop y ejecutar `docker version`. |
| Backend no queda healthy | Ejecutar `docker compose logs backend --tail=200`. |
| Flyway falla | Corregir con una migración nueva; no editar migraciones aplicadas. |
| JWT no inicia | Verificar que `JWT_SECRET_BASE64` sea Base64 y represente al menos 32 bytes. |
| Inbox repite una operación | Reusar exactamente la misma clave y payload; revisar conflictos si cambió el contenido. |
| Hueco de secuencia | Consultar `/sync/conflicts` y el checkpoint; no adelantar manualmente la secuencia. |

No ejecutar `Flyway clean` ni eliminar volúmenes de la base local de trabajo durante recuperación normal.
