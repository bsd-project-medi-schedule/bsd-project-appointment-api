CREATE TABLE IF NOT EXISTS users (
    id               UUID PRIMARY KEY,
    email            TEXT UNIQUE NOT NULL,
    role             INT NOT NULL
);
