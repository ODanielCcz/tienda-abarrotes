CREATE TABLE sync.inbox_operations (
    operation_id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES organization.devices(device_id),
    device_sequence BIGINT NOT NULL CHECK (device_sequence > 0),
    idempotency_key UUID NOT NULL UNIQUE,
    operation_type VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID,
    payload JSONB NOT NULL,
    client_created_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    processed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED','PROCESSING','ACCEPTED','DUPLICATE','REJECTED','CONFLICT')),
    result JSONB,
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    UNIQUE (device_id, device_sequence)
);

CREATE TABLE sync.conflicts (
    conflict_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID NOT NULL UNIQUE REFERENCES sync.inbox_operations(operation_id),
    conflict_type VARCHAR(80) NOT NULL,
    server_state JSONB,
    client_state JSONB,
    resolution VARCHAR(30) CHECK (resolution IN ('SERVER_WINS','CLIENT_WINS','MERGED','MANUAL','REJECTED')),
    resolution_notes VARCHAR(2000),
    resolved_by UUID REFERENCES iam.users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    resolved_at TIMESTAMPTZ
);

CREATE TABLE sync.device_checkpoints (
    device_id UUID PRIMARY KEY REFERENCES organization.devices(device_id),
    last_received_sequence BIGINT NOT NULL DEFAULT 0 CHECK (last_received_sequence >= 0),
    last_processed_sequence BIGINT NOT NULL DEFAULT 0 CHECK (last_processed_sequence >= 0),
    last_sync_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (last_processed_sequence <= last_received_sequence)
);

CREATE TABLE sync.outbox_events (
    outbox_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    correlation_id UUID NOT NULL DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED'))
);

CREATE INDEX idx_inbox_status_received ON sync.inbox_operations(status, received_at);
CREATE INDEX idx_outbox_pending ON sync.outbox_events(created_at) WHERE status = 'PENDING';

