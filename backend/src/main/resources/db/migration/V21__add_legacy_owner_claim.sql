-- The pre-authentication local ledger can be claimed only once by the first
-- verified Finance user. This record is never reset by application code.
CREATE TABLE finance_owner_bootstrap (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    claimed_owner_user_id TEXT,
    claimed_at TEXT
);

INSERT INTO finance_owner_bootstrap (id, claimed_owner_user_id, claimed_at) VALUES (1, NULL, NULL);
