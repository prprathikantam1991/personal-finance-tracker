package com.pradeep.finance.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTransaction(
        String merchant,
        String category,
        BigDecimal averageAmount,
        String cadence,
        int occurrenceCount,
        LocalDate lastTransactionDate,
        LocalDate nextExpectedDate
) {}
