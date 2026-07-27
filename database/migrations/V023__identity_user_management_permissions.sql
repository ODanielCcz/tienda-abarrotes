INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('IDENTITY_USER_READ', 'Consultar usuarios', 'IDENTITY', 'Permite consultar usuarios del sistema'),
    ('IDENTITY_USER_CREATE', 'Crear usuarios', 'IDENTITY', 'Permite crear usuarios del sistema'),
    ('IDENTITY_USER_UPDATE', 'Actualizar usuarios', 'IDENTITY', 'Permite actualizar usuarios del sistema'),
    ('IDENTITY_USER_STATUS', 'Cambiar estado de usuarios', 'IDENTITY', 'Permite activar, bloquear o deshabilitar usuarios'),
    ('IDENTITY_USER_PASSWORD_CHANGE', 'Cambiar contraseña de usuarios', 'IDENTITY', 'Permite cambiar contraseñas de usuarios'),
    ('IDENTITY_USER_ROLE_ASSIGN', 'Asignar roles a usuarios', 'IDENTITY', 'Permite asignar roles existentes a usuarios'),
    ('IDENTITY_ROLE_READ', 'Consultar roles', 'IDENTITY', 'Permite consultar roles del sistema'),
    ('IDENTITY_PERMISSION_READ', 'Consultar permisos', 'IDENTITY', 'Permite consultar permisos del sistema')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN (
      'IDENTITY_USER_READ',
      'IDENTITY_USER_CREATE',
      'IDENTITY_USER_UPDATE',
      'IDENTITY_USER_STATUS',
      'IDENTITY_USER_PASSWORD_CHANGE',
      'IDENTITY_USER_ROLE_ASSIGN',
      'IDENTITY_ROLE_READ',
      'IDENTITY_PERMISSION_READ'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
