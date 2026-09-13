UPDATE statement_imports
SET account_id = (SELECT id FROM accounts WHERE institution = 'bank of america' AND account_type = 'CREDIT_CARD' AND last_four = '7098' LIMIT 1)
WHERE account_id IN (SELECT id FROM accounts WHERE institution = 'bank of america' AND account_type = 'CHECKING' AND last_four = '7788');

DELETE FROM accounts
WHERE institution = 'bank of america' AND account_type = 'CHECKING' AND last_four = '7788'
  AND NOT EXISTS (SELECT 1 FROM transactions WHERE transactions.account_id = accounts.id)
  AND NOT EXISTS (SELECT 1 FROM statement_imports WHERE statement_imports.account_id = accounts.id);
