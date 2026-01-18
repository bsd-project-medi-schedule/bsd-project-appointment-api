CREATE TYPE appointment_status AS ENUM (
    'PENDING',
    'CONFIRMED',
    'REJECTED',
    'COMPLETED',
    'CANCELLED',
    'NOSHOW'
);

CREATE TABLE IF NOT EXISTS appointment (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id          UUID NOT NULL REFERENCES users(id),
    doctor_id           UUID NOT NULL REFERENCES doctor(id),
    office_id           UUID NOT NULL REFERENCES office(id),
    service_id          UUID NOT NULL REFERENCES service(id),
    appointment_time    TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time            TIMESTAMP WITH TIME ZONE NOT NULL,
    status              appointment_status NOT NULL DEFAULT 'PENDING',
    notes               TEXT,
    confirmation_token  VARCHAR(255),
    confirmed_at        TIMESTAMP WITH TIME ZONE,
    reminder_sent       BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_appointment_patient ON appointment(patient_id);
CREATE INDEX idx_appointment_doctor ON appointment(doctor_id);
CREATE INDEX idx_appointment_office ON appointment(office_id);
CREATE INDEX idx_appointment_time ON appointment(appointment_time);
CREATE INDEX idx_appointment_status ON appointment(status);
CREATE INDEX idx_appointment_confirmation ON appointment(confirmation_token);