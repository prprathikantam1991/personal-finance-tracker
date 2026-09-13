package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementSummary(
        BigDecimal statementBalance,
        BigDecimal creditLimit,
        BigDecimal availableCredit,
        BigDecimal feesCharged,
        BigDecimal interestCharged,
        BigDecimal minimumPayment,
        LocalDate paymentDueDate,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate,
        BigDecimal beginningBalance,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        BigDecimal interestEarned,
        BigDecimal annualInterestRate,
        BigDecimal annualPercentageYield
) {
    public StatementSummary(BigDecimal statementBalance, BigDecimal creditLimit, BigDecimal availableCredit, BigDecimal feesCharged, BigDecimal interestCharged, BigDecimal minimumPayment, LocalDate paymentDueDate, LocalDate cycleStartDate, LocalDate cycleEndDate) {
        this(statementBalance, creditLimit, availableCredit, feesCharged, interestCharged, minimumPayment, paymentDueDate, cycleStartDate, cycleEndDate, null, null, null, null, null, null);
    }
    public static StatementSummary empty() { return new StatementSummary(null, null, null, null, null, null, null, null, null); }
}
