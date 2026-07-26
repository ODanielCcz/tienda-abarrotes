CREATE TABLE billing.fiscal_profiles (
    fiscal_profile_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES sales.customers(customer_id),
    rfc VARCHAR(13) NOT NULL,
    legal_name VARCHAR(300) NOT NULL,
    postal_code VARCHAR(5) NOT NULL,
    fiscal_regime_code VARCHAR(5) NOT NULL,
    cfdi_use_code VARCHAR(5),
    email VARCHAR(254),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (customer_id, rfc),
    CHECK (rfc ~ '^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$'),
    CHECK (postal_code ~ '^[0-9]{5}$')
);

CREATE TABLE billing.fiscal_documents (
    fiscal_document_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id UUID REFERENCES sales.sales_orders(sales_order_id),
    fiscal_profile_id UUID NOT NULL REFERENCES billing.fiscal_profiles(fiscal_profile_id),
    document_type VARCHAR(20) NOT NULL CHECK (document_type IN ('INCOME','EXPENSE','PAYMENT','TRANSFER')),
    cfdi_version VARCHAR(5) NOT NULL DEFAULT '4.0' CHECK (cfdi_version = '4.0'),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','READY','STAMP_PENDING','STAMPED','CANCEL_PENDING','CANCELLED','ERROR')),
    series VARCHAR(25),
    folio VARCHAR(50),
    fiscal_uuid UUID UNIQUE,
    issuer_rfc VARCHAR(13) NOT NULL,
    issuer_name VARCHAR(300) NOT NULL,
    receiver_rfc VARCHAR(13) NOT NULL,
    receiver_name VARCHAR(300) NOT NULL,
    payment_form_code VARCHAR(5),
    payment_method_code VARCHAR(5),
    currency_code CHAR(3) NOT NULL DEFAULT 'MXN',
    subtotal NUMERIC(19,4) NOT NULL CHECK (subtotal >= 0),
    discount_total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_total >= 0),
    tax_total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (tax_total >= 0),
    total NUMERIC(19,4) NOT NULL CHECK (total >= 0),
    xml_content TEXT,
    pac_provider VARCHAR(100),
    provider_reference VARCHAR(200),
    issued_at TIMESTAMPTZ,
    stamped_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (issuer_rfc, series, folio)
);

CREATE TABLE billing.fiscal_document_items (
    fiscal_document_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fiscal_document_id UUID NOT NULL REFERENCES billing.fiscal_documents(fiscal_document_id),
    sales_order_item_id UUID REFERENCES sales.sales_order_items(sales_order_item_id),
    sat_product_service_code VARCHAR(8) NOT NULL,
    sat_unit_code VARCHAR(5) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    quantity NUMERIC(18,6) NOT NULL CHECK (quantity > 0),
    unit_value NUMERIC(19,6) NOT NULL CHECK (unit_value >= 0),
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0)
);

CREATE TABLE billing.fiscal_document_relations (
    fiscal_document_id UUID NOT NULL REFERENCES billing.fiscal_documents(fiscal_document_id),
    related_fiscal_uuid UUID NOT NULL,
    relation_type_code VARCHAR(5) NOT NULL,
    PRIMARY KEY (fiscal_document_id, related_fiscal_uuid)
);

CREATE TABLE billing.fiscal_document_events (
    fiscal_document_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fiscal_document_id UUID NOT NULL REFERENCES billing.fiscal_documents(fiscal_document_id),
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('CREATED','READY','STAMP_REQUESTED','STAMPED','STAMP_FAILED','CANCEL_REQUESTED','CANCELLED','CANCEL_FAILED')),
    provider_reference VARCHAR(200),
    error_code VARCHAR(100),
    error_message VARCHAR(2000),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_fiscal_documents_status_date ON billing.fiscal_documents(status, created_at DESC);

