UPDATE transactions SET category = 'Transfer' WHERE lower(description) LIKE '%payment from chk%';
