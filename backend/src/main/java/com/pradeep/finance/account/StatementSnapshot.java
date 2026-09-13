package com.pradeep.finance.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StatementSnapshot(
        LocalDate cycleStartDate, LocalDate cycleEndDate, BigDecimal beginningBalance, BigDecimal endingBalance,
        BigDecimal totalCredits, BigDecimal totalDebits, BigDecimal interestEarned, BigDecimal annualInterestRate,
        BigDecimal annualPercentageYield, BigDecimal creditLimit, BigDecimal availableCredit, BigDecimal feesCharged,
        BigDecimal interestCharged, Instant importedAt
) { }
