CREATE TABLE IF NOT EXISTS doctor (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    office_id         UUID NOT NULL REFERENCES office(id) ON DELETE CASCADE,
    field_of_action   VARCHAR(255) NOT NULL,
    first_name        VARCHAR(255) NOT NULL,
    last_name         VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    phone             VARCHAR(50),
    is_active         BOOLEAN DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT doctor_user_office_unique UNIQUE (user_id, office_id)
);

CREATE INDEX idx_doctor_office ON doctor(office_id);
CREATE INDEX idx_doctor_user ON doctor(user_id);
CREATE INDEX idx_doctor_field ON doctor(field_of_action);