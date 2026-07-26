CREATE TABLE sales.customers (
    customer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_code VARCHAR(50) UNIQUE,
    customer_type VARCHAR(20) NOT NULL DEFAULT 'PERSON' CHECK (customer_type IN ('GENERAL','PERSON','BUSINESS')),
    display_name VARCHAR(200) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(40),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','BLOCKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE inventory.reservations
    ADD CONSTRAINT fk_reservations_customer FOREIGN KEY (customer_id) REFERENCES sales.customers(customer_id);

CREATE TABLE sales.carts (
    cart_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID REFERENCES sales.customers(customer_id),
    branch_id UUID NOT NULL REFERENCES organization.branches(branch_id),
    device_id UUID REFERENCES organization.devices(device_id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CONVERTED','ABANDONED','EXPIRED')),
    currency_code CHAR(3) NOT NULL DEFAULT 'MXN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ
);

CREATE TABLE sales.cart_items (
    cart_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL REFERENCES sales.carts(cart_id),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    unit_price_snapshot NUMERIC(19,4) NOT NULL CHECK (unit_price_snapshot >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (cart_id, product_presentation_id)
);

CREATE TABLE sales.sales_orders (
    sales_order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(80) NOT NULL UNIQUE,
    branch_id UUID NOT NULL REFERENCES organization.branches(branch_id),
    warehouse_id UUID NOT NULL REFERENCES organization.warehouses(warehouse_id),
    customer_id UUID REFERENCES sales.customers(customer_id),
    device_id UUID REFERENCES organization.devices(device_id),
    cart_id UUID REFERENCES sales.carts(cart_id),
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('POS','WEB','MOBILE')),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','RESERVED','PENDING_PAYMENT','CONFIRMED','CANCELLED','PARTIALLY_RETURNED','RETURNED')),
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING','PARTIAL','PAID','REFUNDED','FAILED','CANCELLED')),
    currency_code CHAR(3) NOT NULL DEFAULT 'MXN',
    subtotal NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    discount_total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_total >= 0),
    tax_total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (tax_total >= 0),
    total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (total >= 0),
    idempotency_key UUID NOT NULL UNIQUE,
    client_created_at TIMESTAMPTZ,
    created_by UUID REFERENCES iam.users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);

CREATE TABLE sales.sales_order_items (
    sales_order_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id UUID NOT NULL REFERENCES sales.sales_orders(sales_order_id),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    lot_id UUID REFERENCES inventory.lots(lot_id),
    product_name_snapshot VARCHAR(200) NOT NULL,
    sku_snapshot VARCHAR(80) NOT NULL,
    quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    unit_cost NUMERIC(19,4) NOT NULL CHECK (unit_cost >= 0),
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    tax_rate NUMERIC(9,6) NOT NULL DEFAULT 0 CHECK (tax_rate BETWEEN 0 AND 1),
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
    line_total NUMERIC(19,4) NOT NULL CHECK (line_total >= 0)
);

CREATE TABLE sales.payments (
    payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id UUID NOT NULL REFERENCES sales.sales_orders(sales_order_id),
    payment_method VARCHAR(30) NOT NULL CHECK (payment_method IN ('CASH','CARD','TRANSFER','CREDIT','MIXED','ONLINE_GATEWAY')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','AUTHORIZED','CAPTURED','FAILED','CANCELLED','REFUNDED')),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    provider VARCHAR(80),
    provider_reference VARCHAR(200),
    idempotency_key UUID NOT NULL UNIQUE,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE sales.returns (
    return_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id UUID NOT NULL REFERENCES sales.sales_orders(sales_order_id),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
    reason VARCHAR(1000) NOT NULL,
    total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (total >= 0),
    created_by UUID REFERENCES iam.users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    confirmed_at TIMESTAMPTZ
);

CREATE TABLE sales.return_items (
    return_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id UUID NOT NULL REFERENCES sales.returns(return_id),
    sales_order_item_id UUID NOT NULL REFERENCES sales.sales_order_items(sales_order_item_id),
    quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0)
);

CREATE TABLE sales.credit_accounts (
    credit_account_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL UNIQUE REFERENCES sales.customers(customer_id),
    credit_limit NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (credit_limit >= 0),
    current_balance NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (current_balance >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (current_balance <= credit_limit)
);

CREATE TABLE sales.accounts_receivable (
    account_receivable_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_account_id UUID NOT NULL REFERENCES sales.credit_accounts(credit_account_id),
    sales_order_id UUID NOT NULL UNIQUE REFERENCES sales.sales_orders(sales_order_id),
    original_amount NUMERIC(19,4) NOT NULL CHECK (original_amount > 0),
    outstanding_amount NUMERIC(19,4) NOT NULL CHECK (outstanding_amount >= 0),
    due_at DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','PARTIAL','PAID','OVERDUE','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (outstanding_amount <= original_amount)
);

CREATE TABLE sales.receivable_movements (
    receivable_movement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_receivable_id UUID NOT NULL REFERENCES sales.accounts_receivable(account_receivable_id),
    movement_type VARCHAR(20) NOT NULL CHECK (movement_type IN ('CHARGE','PAYMENT','ADJUSTMENT','CANCELLATION')),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    payment_id UUID REFERENCES sales.payments(payment_id),
    created_by UUID REFERENCES iam.users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_orders_branch_date ON sales.sales_orders(branch_id, created_at DESC);
CREATE INDEX idx_orders_customer_date ON sales.sales_orders(customer_id, created_at DESC);
CREATE INDEX idx_payments_order_status ON sales.payments(sales_order_id, status);
CREATE INDEX idx_receivables_due ON sales.accounts_receivable(status, due_at);

