UPDATE transactions
SET category = 'Transfer'
WHERE lower(description) LIKE '%amex epayment%' OR lower(description) LIKE '%amex e-payment%';
