CREATE TABLE customers (
    id UUID DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    email CITEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uq_customers_phone UNIQUE (phone),
    CONSTRAINT ck_customers_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_customers_phone_not_blank CHECK (btrim(phone) <> '')
);

CREATE TRIGGER trg_customers_set_updated_at
BEFORE UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE appointments (
    id UUID DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    calendar_id UUID NOT NULL,
    offering_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    duration_minutes_snapshot INTEGER NOT NULL,
    calendar_timezone_snapshot VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'scheduled',
    customer_notes TEXT,
    idempotency_key VARCHAR(255) NOT NULL,
    idempotency_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_appointments PRIMARY KEY (id),
    CONSTRAINT fk_appointments_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_appointments_tenant_calendar
        FOREIGN KEY (tenant_id, calendar_id)
        REFERENCES calendars(tenant_id, id),
    CONSTRAINT fk_appointments_tenant_offering
        FOREIGN KEY (tenant_id, offering_id)
        REFERENCES offerings(tenant_id, id),
    CONSTRAINT fk_appointments_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT uq_appointments_tenant_identity UNIQUE (tenant_id, id),
    CONSTRAINT uq_appointments_tenant_idempotency_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_appointments_time_order CHECK (start_at < end_at),
    CONSTRAINT ck_appointments_duration_snapshot CHECK (
        duration_minutes_snapshot > 0
        AND duration_minutes_snapshot % 15 = 0
    ),
    CONSTRAINT ck_appointments_timezone_not_blank
        CHECK (btrim(calendar_timezone_snapshot) <> ''),
    CONSTRAINT ck_appointments_status
        CHECK (status IN ('scheduled', 'cancelled', 'completed', 'no_show')),
    CONSTRAINT ck_appointments_idempotency_key_not_blank
        CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_appointments_idempotency_fingerprint
        CHECK (idempotency_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_appointments_tenant_calendar_start
    ON appointments (tenant_id, calendar_id, start_at);

CREATE INDEX idx_appointments_calendar_start
    ON appointments (calendar_id, start_at);

CREATE INDEX idx_appointments_tenant_offering
    ON appointments (tenant_id, offering_id);

CREATE INDEX idx_appointments_offering_id
    ON appointments (offering_id);

CREATE INDEX idx_appointments_customer_id
    ON appointments (customer_id);

CREATE INDEX idx_appointments_tenant_status_start
    ON appointments (tenant_id, status, start_at);

CREATE TRIGGER trg_appointments_set_updated_at
BEFORE UPDATE ON appointments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE appointment_slots (
    id UUID DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    appointment_id UUID NOT NULL,
    calendar_id UUID NOT NULL,
    slot_start_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_appointment_slots PRIMARY KEY (id),
    CONSTRAINT fk_appointment_slots_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_appointment_slots_tenant_appointment
        FOREIGN KEY (tenant_id, appointment_id)
        REFERENCES appointments(tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_appointment_slots_tenant_calendar
        FOREIGN KEY (tenant_id, calendar_id)
        REFERENCES calendars(tenant_id, id),
    CONSTRAINT uq_appointment_slots_calendar_start
        UNIQUE (calendar_id, slot_start_at)
);

CREATE INDEX idx_appointment_slots_tenant_appointment
    ON appointment_slots (tenant_id, appointment_id);

CREATE INDEX idx_appointment_slots_appointment_id
    ON appointment_slots (appointment_id);

CREATE INDEX idx_appointment_slots_tenant_calendar
    ON appointment_slots (tenant_id, calendar_id);

