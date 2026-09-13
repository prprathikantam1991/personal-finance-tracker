package com.pradeep.finance.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AccountOverviewResponse(
        String id, String name, String institution, AccountType accountType, String lastFour, String currency, Instant createdAt,
        BigDecimal currentApr, BigDecimal promotionalApr, String promotionalAprExpiresOn, BigDecimal creditLimit, BigDecimal penaltyApr,
        BigDecimal statementBalance, BigDecimal availableCredit, BigDecimal creditUtilizationPercent, BigDecimal previousCreditUtilizationPercent,
        BigDecimal previousStatementBalance, BigDecimal previousCreditLimit,
        BigDecimal feesCharged, BigDecimal interestCharged, BigDecimal minimumPayment,
        LocalDate paymentDueDate, LocalDate cycleStartDate, LocalDate cycleEndDate, LocalDate nextExpectedStatementDate,
        BigDecimal beginningBalance, BigDecimal totalCredits, BigDecimal totalDebits, BigDecimal interestEarned, BigDecimal annualInterestRate, BigDecimal annualPercentageYield,
        Instant statementImportedAt
) {}
