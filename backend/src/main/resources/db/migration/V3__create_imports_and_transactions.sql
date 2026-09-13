CREATE TABLE statement_imports (
    id TEXT PRIMARY KEY,
    account_id TEXT,
    original_filename TEXT NOT NULL,
    stored_filename TEXT NOT NULL,
    source_type TEXT NOT NULL,
    status TEXT NOT NULL,
    imported_at TEXT NOT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE TABLE transactions (
    id TEXT PRIMARY KEY,
    import_id TEXT NOT NULL,
    account_id TEXT,
    transaction_date TEXT NOT NULL,
    description TEXT NOT NULL,
    amount NUMERIC NOT NULL,
    balance NUMERIC,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (import_id) REFERENCES statement_imports(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_transactions_import_id ON transactions(import_id);
CREATE INDEX idx_transactions_account_date ON transactions(account_id, transaction_date);
