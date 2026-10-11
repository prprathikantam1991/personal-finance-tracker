ALTER TABLE finance_sessions ADD COLUMN csrf_token TEXT NOT NULL DEFAULT '';

-- Existing opaque sessions predate synchronizer tokens. Require a fresh sign-in
-- rather than silently accepting a session that cannot make protected writes.
DELETE FROM finance_sessions;
