\set ON_ERROR_STOP on
\if :{?role_name}
\else
  \error 'Required variable: role_name'
\endif
\if :{?access_role}
\else
  \error 'Required variable: access_role (tienda_app, tienda_readonly or tienda_audit_reader)'
\endif
\if :{?purpose}
\else
  \error 'Required variable: purpose'
\endif
\if :{?responsible}
\else
  \error 'Required variable: responsible'
\endif

BEGIN;
CREATE ROLE :"role_name" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD NULL;
GRANT :"access_role" TO :"role_name";

WITH registered AS (
    INSERT INTO iam.database_principals
        (role_name, principal_type, purpose, responsible, login_enabled, status)
    VALUES
        (:'role_name', 'SERVICE', :'purpose', :'responsible', TRUE, 'ACTIVE')
    RETURNING database_principal_id, role_name
)
INSERT INTO audit.database_principal_events
    (database_principal_id, role_name_snapshot, event_type, reason, after_state)
SELECT database_principal_id,
       role_name,
       'CREATE',
       'Cuenta creada mediante procedimiento controlado',
       jsonb_build_object('access_role', :'access_role', 'login_enabled', true)
FROM registered;
COMMIT;

\echo 'Role created without a password. Set it interactively with: \\password ' :role_name

