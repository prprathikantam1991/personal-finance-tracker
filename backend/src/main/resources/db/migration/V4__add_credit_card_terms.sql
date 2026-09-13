ALTER TABLE accounts ADD COLUMN current_apr NUMERIC;
ALTER TABLE accounts ADD COLUMN promotional_apr NUMERIC;
ALTER TABLE accounts ADD COLUMN promotional_apr_expires_on TEXT;
ALTER TABLE accounts ADD COLUMN credit_limit NUMERIC;
