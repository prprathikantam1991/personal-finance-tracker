-- Preserve explicitly categorized transactions while correcting known Bank of America
-- statement semantics that were previously left uncategorized.
UPDATE transactions
SET category = 'Transfer'
WHERE category = 'Uncategorized'
  AND lower(description) LIKE '%zelle%';

UPDATE transactions
SET category = 'Transfer'
WHERE category = 'Uncategorized'
  AND (lower(description) LIKE '%payment to crd%'
       OR lower(description) LIKE '%bank of america payment%');

UPDATE transactions
SET category = 'Utilities'
WHERE category = 'Uncategorized'
  AND (lower(description) LIKE '%pseg%'
       OR lower(description) LIKE '%public service des%');
