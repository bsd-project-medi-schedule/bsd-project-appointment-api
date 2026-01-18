CREATE TABLE IF NOT EXISTS doctor_service (
    doctor_id           UUID NOT NULL REFERENCES doctor(id) ON DELETE CASCADE,
    service_id          UUID NOT NULL REFERENCES service(id) ON DELETE CASCADE,
    PRIMARY KEY (doctor_id, service_id)
);

CREATE INDEX idx_doctor_service_doctor ON doctor_service(doctor_id);
CREATE INDEX idx_doctor_service_service ON doctor_service(service_id);