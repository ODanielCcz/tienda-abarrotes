CREATE TABLE catalog.categories (
    category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_category_id UUID REFERENCES catalog.categories(category_id),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (category_id <> parent_category_id)
);

CREATE TABLE catalog.brands (
    brand_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE catalog.units_of_measure (
    unit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    quantity_scale SMALLINT NOT NULL DEFAULT 0 CHECK (quantity_scale BETWEEN 0 AND 6),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE catalog.taxes (
    tax_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    rate NUMERIC(9,6) NOT NULL CHECK (rate BETWEEN 0 AND 1),
    sat_tax_code VARCHAR(10),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE catalog.products (
    product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID REFERENCES catalog.categories(category_id),
    brand_id UUID REFERENCES catalog.brands(brand_id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    product_type VARCHAR(20) NOT NULL DEFAULT 'GOODS' CHECK (product_type IN ('GOODS','SERVICE')),
    tracks_inventory BOOLEAN NOT NULL DEFAULT TRUE,
    tracks_lots BOOLEAN NOT NULL DEFAULT FALSE,
    tracks_expiration BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','DISCONTINUED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (NOT tracks_expiration OR tracks_lots)
);

CREATE TABLE catalog.product_presentations (
    product_presentation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES catalog.products(product_id),
    unit_id UUID NOT NULL REFERENCES catalog.units_of_measure(unit_id),
    tax_id UUID REFERENCES catalog.taxes(tax_id),
    sku VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    conversion_factor NUMERIC(18,6) NOT NULL DEFAULT 1 CHECK (conversion_factor > 0),
    net_content NUMERIC(18,6) CHECK (net_content IS NULL OR net_content > 0),
    minimum_stock NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (minimum_stock >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','DISCONTINUED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE catalog.barcodes (
    barcode_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    barcode VARCHAR(80) NOT NULL UNIQUE,
    barcode_type VARCHAR(20) NOT NULL DEFAULT 'EAN13' CHECK (barcode_type IN ('EAN8','EAN13','UPC','CODE128','INTERNAL','OTHER')),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE UNIQUE INDEX uq_primary_barcode_per_presentation ON catalog.barcodes(product_presentation_id) WHERE is_primary;

CREATE TABLE catalog.price_lists (
    price_list_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'MXN',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE catalog.prices (
    price_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    price_list_id UUID NOT NULL REFERENCES catalog.price_lists(price_list_id),
    branch_id UUID REFERENCES organization.branches(branch_id),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    valid_from TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    valid_until TIMESTAMPTZ,
    created_by UUID REFERENCES iam.users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (valid_until IS NULL OR valid_until > valid_from)
);

CREATE INDEX idx_products_category_status ON catalog.products(category_id, status);
CREATE INDEX idx_presentations_product_status ON catalog.product_presentations(product_id, status);
CREATE INDEX idx_prices_lookup ON catalog.prices(product_presentation_id, branch_id, price_list_id, valid_from DESC);

