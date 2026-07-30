CREATE TABLE billing.issuer_profiles (
    issuer_profile_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES organization.branches(branch_id),
    rfc VARCHAR(13) NOT NULL,
    legal_name VARCHAR(300) NOT NULL,
    postal_code VARCHAR(5) NOT NULL,
    fiscal_regime_code VARCHAR(5) NOT NULL,
    default_series VARCHAR(25),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (branch_id, rfc),
    CHECK (rfc ~ '^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$'),
    CHECK (postal_code ~ '^[0-9]{5}$')
);

CREATE UNIQUE INDEX uq_issuer_profile_active_branch
    ON billing.issuer_profiles (branch_id)
    WHERE status = 'ACTIVE';

ALTER TABLE catalog.products
    ADD COLUMN sat_product_service_code VARCHAR(8)
        CHECK (sat_product_service_code IS NULL OR sat_product_service_code ~ '^[0-9]{8}$');

ALTER TABLE catalog.units_of_measure
    ADD COLUMN sat_unit_code VARCHAR(5)
        CHECK (sat_unit_code IS NULL OR sat_unit_code ~ '^[A-Z0-9]{1,5}$');

ALTER TABLE billing.fiscal_documents
    ADD COLUMN issuer_profile_id UUID REFERENCES billing.issuer_profiles(issuer_profile_id);

CREATE UNIQUE INDEX uq_fiscal_document_income_per_sale
    ON billing.fiscal_documents (sales_order_id)
    WHERE document_type = 'INCOME'
      AND status NOT IN ('CANCELLED','ERROR');

CREATE INDEX idx_fiscal_profiles_customer_status
    ON billing.fiscal_profiles (customer_id, status);

CREATE INDEX idx_fiscal_documents_sale_status
    ON billing.fiscal_documents (sales_order_id, status);

INSERT INTO iam.permissions (code, name, module, description)
VALUES
 ('BILLING_ISSUER_PROFILE_READ', 'Consultar emisores fiscales', 'BILLING', 'Permite consultar perfiles fiscales emisores por sucursal'),
 ('BILLING_ISSUER_PROFILE_CREATE', 'Crear emisores fiscales', 'BILLING', 'Permite crear perfiles fiscales emisores'),
 ('BILLING_ISSUER_PROFILE_UPDATE', 'Actualizar emisores fiscales', 'BILLING', 'Permite actualizar perfiles fiscales emisores'),
 ('BILLING_ISSUER_PROFILE_STATUS', 'Cambiar estado de emisores fiscales', 'BILLING', 'Permite activar o desactivar perfiles fiscales emisores'),
 ('BILLING_FISCAL_PROFILE_READ', 'Consultar perfiles fiscales', 'BILLING', 'Permite consultar perfiles fiscales de clientes'),
 ('BILLING_FISCAL_PROFILE_CREATE', 'Crear perfiles fiscales', 'BILLING', 'Permite crear perfiles fiscales de clientes'),
 ('BILLING_FISCAL_PROFILE_UPDATE', 'Actualizar perfiles fiscales', 'BILLING', 'Permite actualizar perfiles fiscales de clientes'),
 ('BILLING_FISCAL_PROFILE_STATUS', 'Cambiar estado de perfiles fiscales', 'BILLING', 'Permite activar o desactivar perfiles fiscales de clientes'),
 ('BILLING_FISCAL_DOCUMENT_READ', 'Consultar documentos fiscales', 'BILLING', 'Permite consultar documentos fiscales internos'),
 ('BILLING_FISCAL_DOCUMENT_CREATE', 'Crear documentos fiscales', 'BILLING', 'Permite preparar documentos fiscales internos'),
 ('BILLING_FISCAL_DOCUMENT_READY', 'Preparar documentos fiscales', 'BILLING', 'Permite marcar documentos fiscales como listos'),
 ('CATALOG_FISCAL_CLASSIFICATION_UPDATE', 'Actualizar clasificacion fiscal', 'CATALOG', 'Permite asignar codigos SAT a productos y unidades')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN (
    'BILLING_ISSUER_PROFILE_READ',
    'BILLING_ISSUER_PROFILE_CREATE',
    'BILLING_ISSUER_PROFILE_UPDATE',
    'BILLING_ISSUER_PROFILE_STATUS',
    'BILLING_FISCAL_PROFILE_READ',
    'BILLING_FISCAL_PROFILE_CREATE',
    'BILLING_FISCAL_PROFILE_UPDATE',
    'BILLING_FISCAL_PROFILE_STATUS',
    'BILLING_FISCAL_DOCUMENT_READ',
    'BILLING_FISCAL_DOCUMENT_CREATE',
    'BILLING_FISCAL_DOCUMENT_READY',
    'CATALOG_FISCAL_CLASSIFICATION_UPDATE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
