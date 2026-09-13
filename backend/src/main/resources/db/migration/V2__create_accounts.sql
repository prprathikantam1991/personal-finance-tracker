CREATE TABLE accounts (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    institution TEXT NOT NULL,
    account_type TEXT NOT NULL,
    last_four TEXT,
    identity_key TEXT UNIQUE,
    currency TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_accounts_institution_last_four
    ON accounts (institution, last_four);

