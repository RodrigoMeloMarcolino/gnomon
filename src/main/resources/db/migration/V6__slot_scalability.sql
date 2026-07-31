-- The slot row is an ephemeral concurrency lock. Appointment history remains in appointments.
ALTER TABLE appointment_slots
    DROP CONSTRAINT pk_appointment_slots,
    DROP CONSTRAINT uq_appointment_slots_calendar_start;

DROP INDEX idx_appointment_slots_appointment_id;
DROP INDEX idx_appointment_slots_tenant_calendar;

ALTER TABLE appointment_slots
    DROP COLUMN id;

ALTER TABLE appointment_slots
    ADD CONSTRAINT pk_appointment_slots
        PRIMARY KEY (tenant_id, calendar_id, slot_start_at);

CREATE INDEX idx_appointment_slots_slot_start_at
    ON appointment_slots (slot_start_at);
