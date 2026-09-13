package com.pradeep.finance.account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        String id,
        String name,
        String institution,
        AccountType accountType,
        String lastFour,
        String currency,
        Instant createdAt,
        BigDecimal currentApr,
        BigDecimal promotionalApr,
        String promotionalAprExpiresOn,
        BigDecimal creditLimit,
        BigDecimal penaltyApr
) {
    static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getInstitution(),
                account.getAccountType(),
                account.getLastFour(),
                account.getCurrency(),
                account.getCreatedAt(),
                account.getCurrentApr(),
                account.getPromotionalApr(),
                account.getPromotionalAprExpiresOn(),
                account.getCreditLimit(),
                account.getPenaltyApr()
        );
    }
}
