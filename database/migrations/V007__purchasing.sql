CREATE TABLE purchasing.suppliers (
    supplier_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_code VARCHAR(50) NOT NULL UNIQUE,
    legal_name VARCHAR(200) NOT NULL,
    trade_name VARCHAR(200),
    tax_id VARCHAR(20),
    email VARCHAR(254),
    phone VARCHAR(40),
    credit_days INTEGER NOT NULL DEFAULT 0 CHECK (credit_days >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','BLOCKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE inventory.lots
    ADD CONSTRAINT fk_lots_supplier FOREIGN KEY (supplier_id) REFERENCES purchasing.suppliers(supplier_id);

CREATE TABLE purchasing.purchases (
    purchase_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES organization.branches(branch_id),
    warehouse_id UUID NOT NULL REFERENCES organization.warehouses(warehouse_id),
    supplier_id UUID NOT NULL REFERENCES purchasing.suppliers(supplier_id),
    supplier_document VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','CONFIRMED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING','PARTIAL','PAID','CANCELLED')),
    currency_code CHAR(3) NOT NULL DEFAULT 'MXN',
    subtotal NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    discount_total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_total >= 0),
    tax_total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (tax_total >= 0),
    total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (total >= 0),
    idempotency_key UUID UNIQUE,
    purchased_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    confirmed_at TIMESTAMPTZ,
    created_by UUID REFERENCES iam.users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (supplier_id, supplier_document)
);

CREATE TABLE purchasing.purchase_items (
    purchase_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id UUID NOT NULL REFERENCES purchasing.purchases(purchase_id),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    lot_id UUID REFERENCES inventory.lots(lot_id),
    product_name_snapshot VARCHAR(200) NOT NULL,
    sku_snapshot VARCHAR(80) NOT NULL,
    quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    received_quantity NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    unit_cost NUMERIC(19,4) NOT NULL CHECK (unit_cost >= 0),
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
    line_total NUMERIC(19,4) NOT NULL CHECK (line_total >= 0),
    CHECK (received_quantity <= quantity)
);

CREATE TABLE purchasing.accounts_payable (
    account_payable_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES purchasing.suppliers(supplier_id),
    purchase_id UUID NOT NULL UNIQUE REFERENCES purchasing.purchases(purchase_id),
    original_amount NUMERIC(19,4) NOT NULL CHECK (original_amount >= 0),
    outstanding_amount NUMERIC(19,4) NOT NULL CHECK (outstanding_amount >= 0),
    due_at DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','PARTIAL','PAID','OVERDUE','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (outstanding_amount <= original_amount)
);

CREATE TABLE purchasing.payable_movements (
    payable_movement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_payable_id UUID NOT NULL REFERENCES purchasing.accounts_payable(account_payable_id),
    movement_type VARCHAR(20) NOT NULL CHECK (movement_type IN ('CHARGE','PAYMENT','ADJUSTMENT','CANCELLATION')),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    reference VARCHAR(200),
    created_by UUID REFERENCES iam.users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_purchases_supplier_date ON purchasing.purchases(supplier_id, purchased_at DESC);
CREATE INDEX idx_payables_due ON purchasing.accounts_payable(status, due_at);

