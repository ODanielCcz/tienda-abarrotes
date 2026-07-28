INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('IDENTITY_ROLE_CREATE', 'Crear roles', 'IDENTITY', 'Permite crear roles de seguridad'),
    ('IDENTITY_ROLE_UPDATE', 'Actualizar roles', 'IDENTITY', 'Permite actualizar datos de roles de seguridad'),
    ('IDENTITY_ROLE_STATUS', 'Cambiar estado de roles', 'IDENTITY', 'Permite activar o desactivar roles de seguridad'),
    ('IDENTITY_ROLE_PERMISSION_ASSIGN', 'Asignar permisos a roles', 'IDENTITY', 'Permite reemplazar permisos asignados a roles'),
    ('IDENTITY_USER_BRANCH_ASSIGN', 'Asignar sucursales a usuarios', 'IDENTITY', 'Permite reemplazar sucursales activas asignadas a usuarios')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN (
      'IDENTITY_ROLE_CREATE',
      'IDENTITY_ROLE_UPDATE',
      'IDENTITY_ROLE_STATUS',
      'IDENTITY_ROLE_PERMISSION_ASSIGN',
      'IDENTITY_USER_BRANCH_ASSIGN'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;