CREATE TABLE iam.database_principals (
    database_principal_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name VARCHAR(128) NOT NULL UNIQUE,
    principal_type VARCHAR(30) NOT NULL CHECK (principal_type IN ('OWNER','MIGRATION','APPLICATION','READ_ONLY','AUDIT_READER','HUMAN_ADMIN','SERVICE')),
    purpose VARCHAR(500) NOT NULL,
    responsible VARCHAR(200) NOT NULL,
    environment VARCHAR(30) NOT NULL DEFAULT 'LOCAL',
    login_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED','RETIRED')),
    valid_until TIMESTAMPTZ,
    last_reviewed_at TIMESTAMPTZ,
    created_by_database_role NAME NOT NULL DEFAULT current_user,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    disabled_at TIMESTAMPTZ,
    CHECK (valid_until IS NULL OR valid_until > created_at)
);

CREATE TABLE audit.database_principal_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    database_principal_id UUID REFERENCES iam.database_principals(database_principal_id),
    role_name_snapshot VARCHAR(128) NOT NULL,
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('CREATE','REGISTER','ALTER','GRANT','REVOKE','ENABLE_LOGIN','DISABLE_LOGIN','ROTATE_PASSWORD','REVIEW','RETIRE','DROP','RECONCILIATION')),
    executed_by_database_role NAME NOT NULL DEFAULT current_user,
    authorized_by VARCHAR(200),
    reason VARCHAR(1000) NOT NULL,
    before_state JSONB,
    after_state JSONB,
    correlation_id UUID NOT NULL DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE audit.database_access_reviews (
    review_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    database_principal_id UUID NOT NULL REFERENCES iam.database_principals(database_principal_id),
    reviewer VARCHAR(200) NOT NULL,
    result VARCHAR(20) NOT NULL CHECK (result IN ('APPROVED','CHANGES_REQUIRED','REVOKED')),
    notes VARCHAR(2000),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    next_review_at TIMESTAMPTZ
);

CREATE TABLE audit.business_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID,
    actor_user_id UUID,
    actor_role VARCHAR(100),
    branch_id UUID,
    device_id UUID,
    reason VARCHAR(1000),
    before_state JSONB,
    after_state JSONB,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    correlation_id UUID NOT NULL DEFAULT gen_random_uuid(),
    client_ip INET,
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE audit.user_security_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('LOGIN_SUCCESS','LOGIN_FAILURE','LOGOUT','LOCK','UNLOCK','PASSWORD_CHANGE','PASSWORD_RESET','ROLE_CHANGE','PERMISSION_CHANGE','ACCESS_DENIED')),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS','FAILURE','DENIED')),
    correlation_id UUID NOT NULL DEFAULT gen_random_uuid(),
    client_ip INET,
    device_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE audit.sync_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID,
    device_id UUID,
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('RECEIVED','ACCEPTED','DUPLICATE','REJECTED','CONFLICT','RETRIED')),
    reason VARCHAR(1000),
    correlation_id UUID NOT NULL DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE audit.data_access_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID,
    access_type VARCHAR(20) NOT NULL CHECK (access_type IN ('READ','EXPORT','PRINT')),
    purpose VARCHAR(500) NOT NULL,
    correlation_id UUID NOT NULL DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE OR REPLACE FUNCTION audit.reject_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit records are append-only';
END;
$$;

CREATE TRIGGER trg_database_principal_events_append_only BEFORE UPDATE OR DELETE ON audit.database_principal_events FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_database_access_reviews_append_only BEFORE UPDATE OR DELETE ON audit.database_access_reviews FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_business_events_append_only BEFORE UPDATE OR DELETE ON audit.business_events FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_user_security_events_append_only BEFORE UPDATE OR DELETE ON audit.user_security_events FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_sync_events_append_only BEFORE UPDATE OR DELETE ON audit.sync_events FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_data_access_events_append_only BEFORE UPDATE OR DELETE ON audit.data_access_events FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();

INSERT INTO iam.database_principals (role_name, principal_type, purpose, responsible, login_enabled)
VALUES
    (current_user, 'OWNER', 'Propietario local y ejecutor de migraciones', 'Administrador local', TRUE),
    ('tienda_app', 'APPLICATION', 'Privilegios de ejecución para el futuro backend', 'Backend', FALSE),
    ('tienda_readonly', 'READ_ONLY', 'Consultas y reportes sin escritura', 'Soporte', FALSE),
    ('tienda_audit_reader', 'AUDIT_READER', 'Consulta controlada de bitácoras', 'Auditoría', FALSE)
ON CONFLICT (role_name) DO NOTHING;

INSERT INTO audit.database_principal_events (database_principal_id, role_name_snapshot, event_type, reason, after_state)
SELECT database_principal_id, role_name, 'REGISTER', 'Registro inicial de cuenta o rol técnico',
       jsonb_build_object('principal_type', principal_type, 'login_enabled', login_enabled, 'status', status)
FROM iam.database_principals;

CREATE VIEW audit.unregistered_database_roles AS
SELECT r.rolname AS role_name,
       r.rolcanlogin AS login_enabled,
       r.rolsuper AS is_superuser,
       r.rolcreaterole AS can_create_role,
       r.rolcreatedb AS can_create_database
FROM pg_roles r
LEFT JOIN iam.database_principals p ON p.role_name = r.rolname
WHERE p.database_principal_id IS NULL
  AND r.rolname !~ '^pg_';

