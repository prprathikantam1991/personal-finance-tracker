UPDATE transactions SET category = CASE
    WHEN lower(description) LIKE '%payment to crd%' THEN 'Transfer'
    WHEN lower(description) LIKE '%remit2any%' OR lower(description) LIKE '%cybrid-xpat%' THEN 'India Remittance'
    WHEN lower(description) LIKE '%"rent"%' THEN 'Rent'
    WHEN lower(description) LIKE '%bottle king%' THEN 'Food & Drinks'
    ELSE category
END
WHERE category = 'Uncategorized';
