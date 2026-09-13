ALTER TABLE transactions ADD COLUMN transfer_group_id TEXT;

CREATE INDEX idx_transactions_transfer_group ON transactions(transfer_group_id);
