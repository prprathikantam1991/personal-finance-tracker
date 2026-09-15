package com.pradeep.finance.transaction;

import java.util.List;

public record BulkCategoryUpdateResponse(List<TransactionResponse> updatedTransactions, int skippedTransfers) { }
