CREATE TABLE collaborators (
    id UUID DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID,
    display_name VARCHAR(120) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_collaborators PRIMARY KEY (id),
    CONSTRAINT fk_collaborators_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_collaborators_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_collaborators_tenant_identity UNIQUE (tenant_id, id),
    CONSTRAINT ck_collaborators_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE UNIQUE INDEX uq_collaborators_tenant_user
    ON collaborators (tenant_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_collaborators_tenant_id
    ON collaborators (tenant_id);

CREATE INDEX idx_collaborators_user_id
    ON collaborators (user_id);

CREATE TRIGGER trg_collaborators_set_updated_at
BEFORE UPDATE ON collaborators
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE calendars (
    id UUID DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    collaborator_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_calendars PRIMARY KEY (id),
    CONSTRAINT fk_calendars_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_calendars_tenant_collaborator
        FOREIGN KEY (tenant_id, collaborator_id)
        REFERENCES collaborators(tenant_id, id),
    CONSTRAINT uq_calendars_tenant_collaborator UNIQUE (tenant_id, collaborator_id),
    CONSTRAINT uq_calendars_tenant_identity UNIQUE (tenant_id, id),
    CONSTRAINT ck_calendars_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_calendars_timezone_not_blank CHECK (btrim(timezone) <> '')
);

CREATE INDEX idx_calendars_tenant_id
    ON calendars (tenant_id);

CREATE INDEX idx_calendars_collaborator_id
    ON calendars (collaborator_id);

CREATE TRIGGER trg_calendars_set_updated_at
BEFORE UPDATE ON calendars
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE offerings (
    id UUID DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT,
    duration_minutes INTEGER NOT NULL,
    price_cents INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_offerings PRIMARY KEY (id),
    CONSTRAINT fk_offerings_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_offerings_tenant_identity UNIQUE (tenant_id, id),
    CONSTRAINT ck_offerings_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_offerings_duration CHECK (
        duration_minutes > 0
        AND duration_minutes % 15 = 0
    ),
    CONSTRAINT ck_offerings_price CHECK (
        price_cents IS NULL
        OR price_cents >= 0
    )
);

CREATE UNIQUE INDEX uq_offerings_active_tenant_title
    ON offerings (tenant_id, lower(title))
    WHERE is_active;

CREATE INDEX idx_offerings_tenant_active
    ON offerings (tenant_id, is_active);

CREATE TRIGGER trg_offerings_set_updated_at
BEFORE UPDATE ON offerings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE calendar_offerings (
    tenant_id UUID NOT NULL,
    calendar_id UUID NOT NULL,
    offering_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_calendar_offerings PRIMARY KEY (calendar_id, offering_id),
    CONSTRAINT fk_calendar_offerings_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_calendar_offerings_tenant_calendar
        FOREIGN KEY (tenant_id, calendar_id)
        REFERENCES calendars(tenant_id, id),
    CONSTRAINT fk_calendar_offerings_tenant_offering
        FOREIGN KEY (tenant_id, offering_id)
        REFERENCES offerings(tenant_id, id)
);

CREATE INDEX idx_calendar_offerings_tenant_calendar
    ON calendar_offerings (tenant_id, calendar_id);

CREATE INDEX idx_calendar_offerings_tenant_offering
    ON calendar_offerings (tenant_id, offering_id);

CREATE INDEX idx_calendar_offerings_offering_id
    ON calendar_offerings (offering_id);
