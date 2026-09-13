package com.pradeep.finance.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.pradeep.finance.account.AccountType;

public record TransactionResponse(
        String id,
        String accountId,
        String accountName,
        AccountType accountType,
        LocalDate date,
        String description,
        String merchantName,
        BigDecimal amount,
        BigDecimal balance,
        String category,
        String categorizationConfidence,
        String transferGroupId,
        String status
) {
}
