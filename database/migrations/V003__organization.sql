CREATE TABLE organization.branches (
    branch_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    legal_name VARCHAR(200),
    timezone VARCHAR(80) NOT NULL DEFAULT 'America/Mexico_City',
    currency_code CHAR(3) NOT NULL DEFAULT 'MXN',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE TABLE organization.warehouses (
    warehouse_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES organization.branches(branch_id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    warehouse_type VARCHAR(20) NOT NULL DEFAULT 'STORE' CHECK (warehouse_type IN ('STORE','BACKROOM','TRANSIT','DAMAGED','VIRTUAL')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (branch_id, code)
);

CREATE TABLE organization.devices (
    device_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES organization.branches(branch_id),
    warehouse_id UUID REFERENCES organization.warehouses(warehouse_id),
    device_code VARCHAR(80) NOT NULL UNIQUE,
    device_type VARCHAR(20) NOT NULL CHECK (device_type IN ('POS','MOBILE_EMPLOYEE','MOBILE_CUSTOMER','WEB')),
    platform VARCHAR(50),
    app_version VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','BLOCKED','RETIRED')),
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_warehouses_branch ON organization.warehouses(branch_id);
CREATE INDEX idx_devices_branch_status ON organization.devices(branch_id, status);

