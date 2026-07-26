CREATE TABLE inventory.lots (
    lot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    supplier_id UUID,
    lot_number VARCHAR(100) NOT NULL,
    manufactured_at DATE,
    expires_at DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','BLOCKED','EXPIRED','DEPLETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (product_presentation_id, lot_number),
    CHECK (expires_at IS NULL OR manufactured_at IS NULL OR expires_at >= manufactured_at)
);

CREATE TABLE inventory.stock_balances (
    stock_balance_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id UUID NOT NULL REFERENCES organization.warehouses(warehouse_id),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    on_hand_quantity NUMERIC(18,3) NOT NULL DEFAULT 0,
    reserved_quantity NUMERIC(18,3) NOT NULL DEFAULT 0,
    allocated_quantity NUMERIC(18,3) NOT NULL DEFAULT 0,
    available_quantity NUMERIC(18,3) GENERATED ALWAYS AS (on_hand_quantity - reserved_quantity - allocated_quantity) STORED,
    average_unit_cost NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (average_unit_cost >= 0),
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (warehouse_id, product_presentation_id),
    CHECK (on_hand_quantity >= 0),
    CHECK (reserved_quantity >= 0),
    CHECK (allocated_quantity >= 0),
    CHECK (on_hand_quantity >= reserved_quantity + allocated_quantity)
);

CREATE TABLE inventory.lot_balances (
    lot_balance_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id UUID NOT NULL REFERENCES organization.warehouses(warehouse_id),
    lot_id UUID NOT NULL REFERENCES inventory.lots(lot_id),
    on_hand_quantity NUMERIC(18,3) NOT NULL DEFAULT 0,
    reserved_quantity NUMERIC(18,3) NOT NULL DEFAULT 0,
    allocated_quantity NUMERIC(18,3) NOT NULL DEFAULT 0,
    available_quantity NUMERIC(18,3) GENERATED ALWAYS AS (on_hand_quantity - reserved_quantity - allocated_quantity) STORED,
    version INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (warehouse_id, lot_id),
    CHECK (on_hand_quantity >= 0),
    CHECK (reserved_quantity >= 0),
    CHECK (allocated_quantity >= 0),
    CHECK (on_hand_quantity >= reserved_quantity + allocated_quantity)
);

CREATE TABLE inventory.stock_movements (
    stock_movement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES organization.branches(branch_id),
    warehouse_id UUID NOT NULL REFERENCES organization.warehouses(warehouse_id),
    movement_type VARCHAR(30) NOT NULL CHECK (movement_type IN ('PURCHASE_RECEIPT','SALE','SALE_RETURN','SUPPLIER_RETURN','ADJUSTMENT_IN','ADJUSTMENT_OUT','TRANSFER_IN','TRANSFER_OUT','EXPIRATION','DAMAGE','OFFLINE_ALLOCATION','OFFLINE_CONSUMPTION','RESERVATION','RESERVATION_RELEASE')),
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('DRAFT','CONFIRMED','REVERSED')),
    source_type VARCHAR(50),
    source_id UUID,
    reversal_of UUID REFERENCES inventory.stock_movements(stock_movement_id),
    reason VARCHAR(1000),
    idempotency_key UUID UNIQUE,
    created_by UUID REFERENCES iam.users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    confirmed_at TIMESTAMPTZ
);

CREATE TABLE inventory.stock_movement_items (
    stock_movement_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_movement_id UUID NOT NULL REFERENCES inventory.stock_movements(stock_movement_id),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    lot_id UUID REFERENCES inventory.lots(lot_id),
    direction CHAR(3) NOT NULL CHECK (direction IN ('IN','OUT')),
    quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    unit_cost NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
    quantity_before NUMERIC(18,3) NOT NULL,
    quantity_after NUMERIC(18,3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE inventory.reservations (
    reservation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES organization.branches(branch_id),
    customer_id UUID,
    source_type VARCHAR(30) NOT NULL CHECK (source_type IN ('WEB_CART','MOBILE_CART','ORDER')),
    source_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CONFIRMED','EXPIRED','CANCELLED')),
    idempotency_key UUID NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (expires_at > created_at)
);

CREATE TABLE inventory.reservation_items (
    reservation_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL REFERENCES inventory.reservations(reservation_id),
    warehouse_id UUID NOT NULL REFERENCES organization.warehouses(warehouse_id),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    lot_id UUID REFERENCES inventory.lots(lot_id),
    quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    UNIQUE NULLS NOT DISTINCT (reservation_id, warehouse_id, product_presentation_id, lot_id)
);

CREATE TABLE inventory.device_stock_allocations (
    allocation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES organization.devices(device_id),
    warehouse_id UUID NOT NULL REFERENCES organization.warehouses(warehouse_id),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    lot_id UUID REFERENCES inventory.lots(lot_id),
    assigned_quantity NUMERIC(18,3) NOT NULL CHECK (assigned_quantity > 0),
    consumed_quantity NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (consumed_quantity >= 0),
    returned_quantity NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (returned_quantity >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CONSUMED','RETURNED','CANCELLED')),
    idempotency_key UUID NOT NULL UNIQUE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    CHECK (consumed_quantity + returned_quantity <= assigned_quantity)
);

CREATE TABLE inventory.inventory_counts (
    inventory_count_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id UUID NOT NULL REFERENCES organization.warehouses(warehouse_id),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','COUNTING','CONFIRMED','CANCELLED')),
    started_by UUID REFERENCES iam.users(user_id),
    confirmed_by UUID REFERENCES iam.users(user_id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    confirmed_at TIMESTAMPTZ
);

CREATE TABLE inventory.inventory_count_items (
    inventory_count_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_count_id UUID NOT NULL REFERENCES inventory.inventory_counts(inventory_count_id),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    lot_id UUID REFERENCES inventory.lots(lot_id),
    expected_quantity NUMERIC(18,3) NOT NULL,
    counted_quantity NUMERIC(18,3),
    UNIQUE NULLS NOT DISTINCT (inventory_count_id, product_presentation_id, lot_id)
);

CREATE INDEX idx_lots_fefo ON inventory.lots(product_presentation_id, expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_stock_available ON inventory.stock_balances(warehouse_id, product_presentation_id, available_quantity);
CREATE INDEX idx_movements_source ON inventory.stock_movements(source_type, source_id);
CREATE INDEX idx_reservations_expiration ON inventory.reservations(expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_device_allocations_active ON inventory.device_stock_allocations(device_id, status);
