-- SQLite's original accounts.identity_key uniqueness is global. Encode the
-- owner into the persisted technical key so two users can save the same bank
-- and last four digits without colliding, while preserving existing data.
UPDATE accounts
SET identity_key = owner_user_id || '|' || identity_key
WHERE identity_key IS NOT NULL
  AND identity_key NOT LIKE owner_user_id || '|%';
