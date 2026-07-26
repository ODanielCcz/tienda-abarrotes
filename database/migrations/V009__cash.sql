CREATE TABLE organization.cash_registers (
    cash_register_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES organization.branches(branch_id),
    device_id UUID REFERENCES organization.devices(device_id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','MAINTENANCE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (branch_id, code)
);

CREATE TABLE cash.cash_sessions (
    cash_session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_register_id UUID NOT NULL REFERENCES organization.cash_registers(cash_register_id),
    opened_by UUID NOT NULL REFERENCES iam.users(user_id),
    closed_by UUID REFERENCES iam.users(user_id),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED','CANCELLED')),
    opening_amount NUMERIC(19,4) NOT NULL CHECK (opening_amount >= 0),
    expected_amount NUMERIC(19,4),
    counted_amount NUMERIC(19,4),
    difference_amount NUMERIC(19,4),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    closed_at TIMESTAMPTZ,
    notes VARCHAR(1000),
    CHECK ((status = 'OPEN' AND closed_at IS NULL) OR status <> 'OPEN')
);

CREATE UNIQUE INDEX uq_open_session_per_register ON cash.cash_sessions(cash_register_id) WHERE status = 'OPEN';

CREATE TABLE cash.cash_movements (
    cash_movement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_session_id UUID NOT NULL REFERENCES cash.cash_sessions(cash_session_id),
    movement_type VARCHAR(30) NOT NULL CHECK (movement_type IN ('OPENING','SALE','REFUND','CASH_IN','CASH_OUT','CLOSING','ADJUSTMENT')),
    direction CHAR(3) NOT NULL CHECK (direction IN ('IN','OUT')),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    payment_id UUID REFERENCES sales.payments(payment_id),
    reference VARCHAR(200),
    reason VARCHAR(1000),
    created_by UUID NOT NULL REFERENCES iam.users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_cash_movements_session_date ON cash.cash_movements(cash_session_id, created_at);

