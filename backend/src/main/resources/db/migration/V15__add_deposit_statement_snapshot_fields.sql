ALTER TABLE statement_imports ADD COLUMN beginning_balance NUMERIC;
ALTER TABLE statement_imports ADD COLUMN total_credits NUMERIC;
ALTER TABLE statement_imports ADD COLUMN total_debits NUMERIC;
ALTER TABLE statement_imports ADD COLUMN interest_earned NUMERIC;
ALTER TABLE statement_imports ADD COLUMN annual_interest_rate NUMERIC;
ALTER TABLE statement_imports ADD COLUMN annual_percentage_yield NUMERIC;
