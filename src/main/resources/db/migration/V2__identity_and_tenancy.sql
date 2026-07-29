CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_sub VARCHAR(255) NOT NULL,
    email CITEXT NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_keycloak_sub UNIQUE (keycloak_sub),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_keycloak_sub_not_blank CHECK (btrim(keycloak_sub) <> ''),
    CONSTRAINT ck_users_email_lowercase CHECK (email::TEXT = lower(email::TEXT)),
    CONSTRAINT ck_users_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE TRIGGER trg_users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'BRL',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_tenants_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_tenants_slug_lowercase CHECK (
        slug = lower(slug)
        AND slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'
    ),
    CONSTRAINT ck_tenants_timezone_not_blank CHECK (btrim(timezone) <> ''),
    CONSTRAINT ck_tenants_currency_code CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_tenants_status CHECK (status IN ('active', 'suspended'))
);

CREATE TRIGGER trg_tenants_set_updated_at
BEFORE UPDATE ON tenants
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE tenant_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_memberships_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_tenant_memberships_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_tenant_memberships_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT ck_tenant_memberships_role CHECK (role IN ('owner', 'admin', 'staff'))
);

CREATE INDEX idx_tenant_memberships_user_id
    ON tenant_memberships (user_id);

CREATE INDEX idx_tenant_memberships_tenant_role
    ON tenant_memberships (tenant_id, role);

CREATE TRIGGER trg_tenant_memberships_set_updated_at
BEFORE UPDATE ON tenant_memberships
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
