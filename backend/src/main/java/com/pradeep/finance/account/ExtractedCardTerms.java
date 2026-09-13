package com.pradeep.finance.account;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExtractedCardTerms(
        BigDecimal currentApr,
        BigDecimal promotionalApr,
        LocalDate promotionalAprExpiresOn,
        BigDecimal creditLimit,
        BigDecimal penaltyApr
) {
    public boolean hasValues() {
        return currentApr != null || promotionalApr != null || promotionalAprExpiresOn != null || creditLimit != null || penaltyApr != null;
    }
}
