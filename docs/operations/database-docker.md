# Operación De PostgreSQL Con Docker

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Código | TDA-OPS-BD-001 |
| Versión | v0.1 |
| Estado | Borrador |
| Responsable | Por definir |
| Fecha | 2026-07-12 |
| Clasificación | Interno |

## Control De Versiones

| Versión | Fecha | Autor | Cambio | Estado |
|---|---|---|---|---|
| v0.1 | 2026-07-12 | Equipo del proyecto | Guía inicial | Borrador |

## Requisitos

- Docker Desktop con motor Linux iniciado.
- Docker Compose v2 o posterior.
- PowerShell 7 recomendado.

## Configuración

```powershell
Set-Location 'C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes'
Copy-Item .env.example .env
```

Edita `.env` y reemplaza las dos contraseñas. El archivo está ignorado por Git.

## Flujo Normal

```powershell
.\scripts\db-up.ps1
.\scripts\db-migrate.ps1
.\scripts\db-seed.ps1
.\scripts\db-test.ps1
```

Levantar pgAdmin:

```powershell
.\scripts\db-tools.ps1
```

Detener sin borrar datos:

```powershell
.\scripts\db-down.ps1
```

## Migraciones

- Crear siempre un archivo `VNNN__descripcion.sql` nuevo.
- No modificar una migración aplicada.
- Ejecutar `db-migrate.ps1`, que aplica y valida checksums.
- Los seeds de desarrollo no forman parte de Flyway ni deben ejecutarse en producción.

## Cuentas PostgreSQL

Los roles `tienda_app`, `tienda_readonly` y `tienda_audit_reader` son grupos sin inicio de sesión. Para crear una cuenta técnica controlada:

```powershell
docker compose exec database psql -U tienda_owner -d tienda_abarrotes `
  -v role_name=servicio_local `
  -v access_role=tienda_app `
  -v purpose='Backend local' `
  -v responsible='Equipo backend' `
  -f /workspace/admin/create_database_principal.sql
```

La cuenta se crea sin contraseña. Configúrala interactivamente con `\password servicio_local` dentro de `psql`; nunca pongas la contraseña en scripts o bitácoras.

Para bloquearla:

```powershell
docker compose exec database psql -U tienda_owner -d tienda_abarrotes `
  -v role_name=servicio_local `
  -v reason='Fin de uso local' `
  -f /workspace/admin/disable_database_principal.sql
```

Comprobar cuentas no registradas y eventos pgAudit:

```powershell
.\scripts\db-audit-check.ps1
```

## Respaldo Y Restauración

```powershell
.\scripts\db-backup.ps1
.\scripts\db-restore.ps1 -BackupPath '.\data\local-only\backups\archivo.backup'
```

Los respaldos se guardan en `data/local-only` y no se versionan.

## Reinicio Destructivo

```powershell
.\scripts\db-reset.ps1
```

El script exige escribir `ELIMINAR` y borra los volúmenes locales. No usar contra ambientes compartidos.

## Solución De Problemas

| Síntoma | Acción |
|---|---|
| No se conecta al motor | Iniciar Docker Desktop y repetir `docker version`. |
| Puerto 5432 ocupado | Cambiar `POSTGRES_PORT` en `.env`. |
| Flyway reporta checksum | No editar migraciones aplicadas; crear una nueva. |
| pgAudit no carga | Reconstruir con `db-up.ps1`; la extensión se compila en la imagen personalizada. |
| Seed falla por esquema ausente | Ejecutar primero `db-migrate.ps1`. |

