ALTER TABLE accounts ADD COLUMN archived INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN archived_at TEXT;

-- Earlier imports represented legacy Bilt •9484 as a Wells Fargo Autograph card.
-- Keep its stable account id and linked ledger history, but remove it from active finance views.
UPDATE accounts
SET name = 'Legacy Bilt •9484',
    institution = 'bilt',
    identity_key = owner_user_id || '|bilt|9484',
    archived = 1,
    archived_at = CURRENT_TIMESTAMP
WHERE institution = 'wells fargo'
  AND account_type = 'CREDIT_CARD'
  AND last_four = '9484'
  AND name LIKE 'Wells Fargo Autograph%';
