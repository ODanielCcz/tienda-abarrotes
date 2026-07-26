\set ON_ERROR_STOP on
\if :{?role_name}
\else
  \error 'Required variable: role_name'
\endif
\if :{?reason}
\else
  \error 'Required variable: reason'
\endif

BEGIN;
ALTER ROLE :"role_name" NOLOGIN;
WITH changed AS (
    UPDATE iam.database_principals
    SET login_enabled = FALSE,
        status = 'DISABLED',
        disabled_at = clock_timestamp(),
        updated_at = clock_timestamp()
    WHERE role_name = :'role_name'
    RETURNING database_principal_id, role_name
)
INSERT INTO audit.database_principal_events
    (database_principal_id, role_name_snapshot, event_type, reason, after_state)
SELECT database_principal_id, role_name, 'DISABLE_LOGIN', :'reason', jsonb_build_object('login_enabled', false, 'status', 'DISABLED')
FROM changed;
COMMIT;

