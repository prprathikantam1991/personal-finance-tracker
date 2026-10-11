-- V7 foundation: existing local data belongs to the single local owner until
-- the Auth Service handoff is enabled. New code must scope records by this ID.
ALTER TABLE accounts ADD COLUMN owner_user_id TEXT NOT NULL DEFAULT 'local-owner';
ALTER TABLE statement_imports ADD COLUMN owner_user_id TEXT NOT NULL DEFAULT 'local-owner';
ALTER TABLE transactions ADD COLUMN owner_user_id TEXT NOT NULL DEFAULT 'local-owner';
ALTER TABLE category_rules ADD COLUMN owner_user_id TEXT NOT NULL DEFAULT 'local-owner';
ALTER TABLE assistant_conversations ADD COLUMN owner_user_id TEXT NOT NULL DEFAULT 'local-owner';

CREATE INDEX idx_accounts_owner ON accounts (owner_user_id);
CREATE INDEX idx_statement_imports_owner ON statement_imports (owner_user_id);
CREATE INDEX idx_transactions_owner_date ON transactions (owner_user_id, transaction_date);
CREATE INDEX idx_category_rules_owner ON category_rules (owner_user_id);
CREATE INDEX idx_assistant_conversations_owner ON assistant_conversations (owner_user_id);
