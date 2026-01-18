CREATE TABLE IF NOT EXISTS service_type (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name              VARCHAR(255) NOT NULL UNIQUE,
    description       TEXT
);

CREATE TABLE IF NOT EXISTS service (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type_id             UUID NOT NULL REFERENCES service_type(id),
    office_id           UUID NOT NULL REFERENCES office(id) ON DELETE CASCADE,
    duration_in_minutes INTEGER NOT NULL,
    price               NUMERIC(12,2) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    notes               TEXT,
    CONSTRAINT positive_duration CHECK (duration_in_minutes > 0),
    CONSTRAINT positive_price CHECK (price >= 0),
    CONSTRAINT service_office_type_unique UNIQUE (office_id, type_id)
);

CREATE INDEX idx_service_office ON service(office_id);
CREATE INDEX idx_service_type ON service(type_id);