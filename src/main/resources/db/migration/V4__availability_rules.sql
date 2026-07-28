CREATE TABLE availability_rules (
    id UUID DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    calendar_id UUID NOT NULL,
    weekday SMALLINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_availability_rules PRIMARY KEY (id),
    CONSTRAINT fk_availability_rules_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_availability_rules_tenant_calendar
        FOREIGN KEY (tenant_id, calendar_id)
        REFERENCES calendars(tenant_id, id),
    CONSTRAINT ck_availability_rules_weekday
        CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_availability_rules_time_order
        CHECK (start_time < end_time),
    CONSTRAINT ck_availability_rules_time_alignment
        CHECK (
            EXTRACT(MINUTE FROM start_time)::INTEGER % 15 = 0
            AND EXTRACT(SECOND FROM start_time) = 0
            AND EXTRACT(MINUTE FROM end_time)::INTEGER % 15 = 0
            AND EXTRACT(SECOND FROM end_time) = 0
        )
);

CREATE INDEX idx_availability_rules_tenant_calendar_weekday
    ON availability_rules (tenant_id, calendar_id, weekday);

CREATE INDEX idx_availability_rules_calendar_weekday
    ON availability_rules (calendar_id, weekday);

CREATE TRIGGER trg_availability_rules_set_updated_at
BEFORE UPDATE ON availability_rules
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
