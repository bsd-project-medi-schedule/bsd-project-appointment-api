CREATE TABLE IF NOT EXISTS office (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    specialization    VARCHAR(255),
    location          VARCHAR(255) NOT NULL,
    phone             VARCHAR(50),
    email             VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);