package com.pradeep.finance.account;

public record AccountIdentificationResponse(
        AccountMatchStatus status,
        AccountResponse account,
        String message
) {
    public enum AccountMatchStatus {
        MATCHED,
        CREATED,
        CONFIRMATION_REQUIRED
    }
}

