CREATE TABLE baseentity (
    deploycode TEXT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    icon TEXT
);