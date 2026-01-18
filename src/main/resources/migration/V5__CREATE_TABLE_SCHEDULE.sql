CREATE TABLE IF NOT EXISTS doctor_schedule (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    doctor_id         UUID NOT NULL REFERENCES doctor(id) ON DELETE CASCADE,
    day_of_week       INTEGER NOT NULL CHECK (day_of_week >= 0 AND day_of_week <= 6),
    start_time        TIME NOT NULL,
    end_time          TIME NOT NULL,
    slot_duration_min INTEGER NOT NULL DEFAULT 30,
    is_active         BOOLEAN DEFAULT TRUE,
    CONSTRAINT schedule_time_check CHECK (start_time < end_time),
    CONSTRAINT schedule_doctor_day_unique UNIQUE (doctor_id, day_of_week)
);

CREATE INDEX idx_schedule_doctor ON doctor_schedule(doctor_id);