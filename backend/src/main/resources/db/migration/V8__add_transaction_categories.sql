ALTER TABLE transactions ADD COLUMN category TEXT NOT NULL DEFAULT 'Uncategorized';

UPDATE transactions SET category = CASE
    WHEN lower(description) LIKE '%payroll%' THEN 'Income'
    WHEN lower(description) LIKE '%transfer%' OR lower(description) LIKE '%e-payment%' OR lower(description) LIKE '%ccpymt%' OR lower(description) LIKE '%thank you%' THEN 'Transfer'
    WHEN lower(description) LIKE '%costco gas%' THEN 'Gas & Fuel'
    WHEN lower(description) LIKE '%t-mobile%' OR lower(description) LIKE '%frontier online%' THEN 'Utilities'
    WHEN lower(description) LIKE '%planet fitness%' THEN 'Fitness'
    WHEN lower(description) LIKE '%nissan%' THEN 'Auto & Transport'
    WHEN lower(description) LIKE '%patel%' OR lower(description) LIKE '%subzi mandi%' OR lower(description) LIKE '%walmart%' THEN 'Groceries'
    WHEN lower(description) LIKE '%robinhood%' THEN 'Investments'
    ELSE 'Uncategorized'
END;
