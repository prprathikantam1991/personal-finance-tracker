CREATE TABLE finance_sessions (
    id TEXT PRIMARY KEY,
    owner_user_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    last_seen_at TEXT NOT NULL,
    expires_at TEXT NOT NULL
);

CREATE INDEX idx_finance_sessions_owner ON finance_sessions (owner_user_id);
CREATE INDEX idx_finance_sessions_expiry ON finance_sessions (expires_at);
