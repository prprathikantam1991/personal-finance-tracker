package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedTransaction(LocalDate date, String description, BigDecimal amount, BigDecimal balance, String sourceAccountLastFour) {
    public ParsedTransaction(LocalDate date, String description, BigDecimal amount, BigDecimal balance) {
        this(date, description, amount, balance, null);
    }
}
