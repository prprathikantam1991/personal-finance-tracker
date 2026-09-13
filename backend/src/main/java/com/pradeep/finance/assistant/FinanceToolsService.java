package com.pradeep.finance.assistant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.pradeep.finance.account.AccountOverviewResponse;
import com.pradeep.finance.account.AccountOverviewService;
import com.pradeep.finance.account.AccountType;
import com.pradeep.finance.dashboard.DashboardService;
import com.pradeep.finance.dashboard.DashboardSummary;
import com.pradeep.finance.dashboard.MerchantSpending;
import com.pradeep.finance.recurring.RecurringTransaction;
import com.pradeep.finance.recurring.RecurringTransactionService;
import com.pradeep.finance.transaction.TransactionResponse;
import com.pradeep.finance.transaction.TransactionService;

/** Read-only, structured finance operations that an assistant may call in V3. */
@Service
public class FinanceToolsService {
    private final DashboardService dashboardService;
    private final AccountOverviewService accountOverviewService;
    private final RecurringTransactionService recurringTransactionService;
    private final TransactionService transactionService;

    public FinanceToolsService(DashboardService dashboardService, AccountOverviewService accountOverviewService,
                               RecurringTransactionService recurringTransactionService, TransactionService transactionService) {
        this.dashboardService = dashboardService;
        this.accountOverviewService = accountOverviewService;
        this.recurringTransactionService = recurringTransactionService;
        this.transactionService = transactionService;
    }

    public DashboardSummary monthlySummary(LocalDate from, LocalDate to) { return dashboardService.summary(from, to); }
    public List<DashboardSummary.CategoryTotal> categorySpending(LocalDate from, LocalDate to) { return dashboardService.summary(from, to).categorySpending(); }
    public List<MerchantSpending> merchantSpending(LocalDate from, LocalDate to) { return dashboardService.merchantSpending(from, to); }
    public List<RecurringTransaction> recurringActivity() { return recurringTransactionService.findRecurringTransactions(); }

    public PeriodComparison comparePeriods(LocalDate from, LocalDate to, LocalDate compareFrom, LocalDate compareTo) {
        return new PeriodComparison(new Period(from, to, dashboardService.summary(from, to)), new Period(compareFrom, compareTo, dashboardService.summary(compareFrom, compareTo)));
    }

    public CreditUtilization creditUtilization() {
        List<AccountOverviewResponse> cards = accountOverviewService.list().stream()
                .filter(account -> account.accountType() == AccountType.CREDIT_CARD && account.creditLimit() != null && account.creditLimit().signum() > 0).toList();
        BigDecimal limit = cards.stream().map(AccountOverviewResponse::creditLimit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal used = cards.stream().map(account -> nonNegative(account.statementBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AccountOverviewResponse> previousCards = cards.stream().filter(account -> account.previousCreditLimit() != null && account.previousCreditLimit().signum() > 0 && account.previousStatementBalance() != null).toList();
        BigDecimal previousLimit = previousCards.stream().map(AccountOverviewResponse::previousCreditLimit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousUsed = previousCards.stream().map(account -> nonNegative(account.previousStatementBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CardUtilization> byCard = cards.stream().map(account -> new CardUtilization(account.name(), account.lastFour(), account.statementBalance(), account.creditLimit(), account.availableCredit(), account.creditUtilizationPercent(), account.previousCreditUtilizationPercent())).toList();
        return new CreditUtilization(limit, used, limit.subtract(used).max(BigDecimal.ZERO), percent(used, limit), percent(previousUsed, previousLimit), cards.size(), previousCards.size(), byCard);
    }

    public List<TransactionResponse> searchTransactions(String accountId, LocalDate from, LocalDate to, String category, String merchant) {
        return transactionService.list(accountId, from, to).stream()
                .filter(transaction -> "CONFIRMED".equals(transaction.status()))
                .filter(transaction -> category == null || category.isBlank() || category.equalsIgnoreCase(transaction.category()))
                .filter(transaction -> merchant == null || merchant.isBlank() || transaction.merchantName().toLowerCase().contains(merchant.toLowerCase()))
                .limit(200).toList();
    }

    private BigDecimal nonNegative(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO); }
    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) { return denominator.signum() <= 0 ? null : numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP); }

    public record Period(LocalDate from, LocalDate to, DashboardSummary summary) {}
    public record PeriodComparison(Period selectedPeriod, Period comparisonPeriod) {}
    public record CreditUtilization(BigDecimal totalLimit, BigDecimal utilized, BigDecimal available, BigDecimal utilizationPercent,
                                    BigDecimal previousUtilizationPercent, int cardCount, int cardsWithPreviousSnapshot, List<CardUtilization> cards) {}
    public record CardUtilization(String accountName, String lastFour, BigDecimal statementBalance, BigDecimal creditLimit,
                                  BigDecimal availableCredit, BigDecimal utilizationPercent, BigDecimal previousUtilizationPercent) {}
}
