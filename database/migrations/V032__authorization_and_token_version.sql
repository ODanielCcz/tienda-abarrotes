ALTER TABLE iam.users
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE iam.users
    ADD CONSTRAINT ck_users_auth_version_non_negative
    CHECK (auth_version >= 0);

CREATE INDEX idx_users_authentication_state
    ON iam.users (user_id, status, auth_version);
