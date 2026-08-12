CREATE TABLE sync.device_user_bindings (
    device_id UUID PRIMARY KEY REFERENCES organization.devices(device_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES iam.users(user_id) ON DELETE CASCADE,
    bound_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_sync_device_user_bindings_user
    ON sync.device_user_bindings (user_id);
