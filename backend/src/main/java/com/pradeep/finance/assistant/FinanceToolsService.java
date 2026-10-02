package com.pradeep.finance.assistant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.pradeep.finance.account.AccountOverviewResponse;
import com.pradeep.finance.account.AccountOverviewService;
import com.pradeep.finance.account.AccountHistoryResponse;
import com.pradeep.finance.account.AccountType;
import com.pradeep.finance.account.StatementSnapshot;
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

    /** Compact account snapshots give the model account IDs without sending the full account record. */
    public List<AccountContext> accountOverview() {
        return accountOverviewService.list().stream().map(this::accountContext).toList();
    }

    /** A bounded statement history for one account, selected only after the model has its account ID. */
    public AccountHistoryContext accountHistory(String accountId) {
        AccountHistoryResponse history = accountOverviewService.history(accountId);
        List<AccountSnapshotContext> snapshots = history.snapshots().stream()
                .skip(Math.max(0, history.snapshots().size() - 12L))
                .map(this::snapshotContext).toList();
        return new AccountHistoryContext(accountContext(history.account()), snapshots);
    }

    public PeriodComparison comparePeriods(LocalDate from, LocalDate to, LocalDate compareFrom, LocalDate compareTo) {
        return new PeriodComparison(new Period(from, to, dashboardService.summary(from, to)), new Period(compareFrom, compareTo, dashboardService.summary(compareFrom, compareTo)));
    }

    /** A bounded, purpose-built lookup for questions such as “compare groceries in July and August”. */
    public CategoryComparison compareCategory(String category, LocalDate from, LocalDate to, LocalDate compareFrom, LocalDate compareTo) {
        BigDecimal selected = categoryAmount(category, from, to);
        BigDecimal comparison = categoryAmount(category, compareFrom, compareTo);
        Map<String, MerchantPeriodTotal> merchants = new LinkedHashMap<>();
        dashboardService.merchantSpending(from, to, category).forEach(item -> merchants.put(item.merchant(), new MerchantPeriodTotal(item.merchant(), item.amount(), BigDecimal.ZERO)));
        dashboardService.merchantSpending(compareFrom, compareTo, category).forEach(item -> merchants.merge(item.merchant(),
                new MerchantPeriodTotal(item.merchant(), BigDecimal.ZERO, item.amount()),
                (left, right) -> new MerchantPeriodTotal(left.merchant(), left.selectedAmount(), right.comparisonAmount())));
        List<MerchantPeriodTotal> merchantChanges = merchants.values().stream()
                .map(item -> new MerchantPeriodTotal(item.merchant(), item.selectedAmount(), item.comparisonAmount()))
                .sorted(Comparator.comparing((MerchantPeriodTotal item) -> item.selectedAmount().subtract(item.comparisonAmount())).reversed())
                .limit(10).toList();
        return new CategoryComparison(category, new CategoryPeriod(from, to, selected), new CategoryPeriod(compareFrom, compareTo, comparison), merchantChanges);
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

    /**
     * Calculates a cent-accurate payment for a strict utilization target. This is a
     * transparent calculation over saved card snapshots, not model-generated math
     * or a payment action.
     */
    public CreditPaydownPlan creditPaydownPlan(BigDecimal targetUtilizationPercent) {
        if (targetUtilizationPercent == null || targetUtilizationPercent.signum() <= 0
                || targetUtilizationPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Target utilization must be greater than 0 and no more than 100.");
        }
        List<AccountOverviewResponse> cards = accountOverviewService.list().stream()
                .filter(account -> account.accountType() == AccountType.CREDIT_CARD && account.creditLimit() != null
                        && account.creditLimit().signum() > 0)
                .sorted(Comparator.comparing((AccountOverviewResponse account) -> precisePercent(nonNegative(account.statementBalance()), account.creditLimit()),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(account -> valueOrZero(account.currentApr()), Comparator.reverseOrder())
                        .thenComparing(this::promotionExpiry))
                .toList();
        BigDecimal totalLimit = cards.stream().map(AccountOverviewResponse::creditLimit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal utilized = cards.stream().map(account -> nonNegative(account.statementBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalLimit.signum() <= 0) {
            return new CreditPaydownPlan(false, "No saved credit-card limits are available for a utilization plan.",
                    targetUtilizationPercent, totalLimit, utilized, null, null, BigDecimal.ZERO, null, List.of());
        }

        // Payments settle in cents. The largest cent balance strictly below the threshold
        // is ceil(limit * target / 100) - $0.01. This makes "below 10%" exact, not "10%".
        BigDecimal targetBalance = totalLimit.multiply(targetUtilizationPercent).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        BigDecimal maximumBalanceBelowTarget = targetBalance.setScale(2, RoundingMode.CEILING).subtract(BigDecimal.valueOf(0.01)).max(BigDecimal.ZERO);
        BigDecimal requiredPayment = utilized.subtract(maximumBalanceBelowTarget).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal projectedUtilized = utilized.subtract(requiredPayment).max(BigDecimal.ZERO);
        BigDecimal remainingPayment = requiredPayment;
        List<CardPaydownPriority> priorities = new java.util.ArrayList<>();
        for (AccountOverviewResponse card : cards) {
            BigDecimal balance = nonNegative(card.statementBalance());
            BigDecimal suggestedPayment = balance.min(remainingPayment).setScale(2, RoundingMode.HALF_UP);
            remainingPayment = remainingPayment.subtract(suggestedPayment);
            priorities.add(new CardPaydownPriority(card.name(), card.lastFour(), balance, card.creditLimit(),
                    precisePercent(balance, card.creditLimit()), card.currentApr(), card.promotionalApr(), card.promotionalAprExpiresOn(),
                    suggestedPayment, "Ranked by highest utilization, then current APR, then promotional APR expiry."));
        }
        String status = requiredPayment.signum() == 0
                ? "Current utilization is already strictly below the requested target."
                : "Pay the calculated amount to reach a balance strictly below the requested utilization target.";
        return new CreditPaydownPlan(true, status, targetUtilizationPercent, totalLimit, utilized, percent(utilized, totalLimit),
                maximumBalanceBelowTarget, requiredPayment, precisePercent(projectedUtilized, totalLimit), priorities);
    }

    public List<TransactionResponse> searchTransactions(String accountId, LocalDate from, LocalDate to, String category, String merchant) {
        return transactionService.list(accountId, from, to).stream()
                .filter(transaction -> "CONFIRMED".equals(transaction.status()))
                .filter(transaction -> category == null || category.isBlank() || category.equalsIgnoreCase(transaction.category()))
                .filter(transaction -> merchant == null || merchant.isBlank() || transaction.merchantName().toLowerCase().contains(merchant.toLowerCase()))
                .limit(200).toList();
    }

    private BigDecimal nonNegative(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO); }
    private BigDecimal valueOrZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private LocalDate promotionExpiry(AccountOverviewResponse account) {
        if (account.promotionalAprExpiresOn() == null || account.promotionalAprExpiresOn().isBlank()) return LocalDate.MAX;
        try { return LocalDate.parse(account.promotionalAprExpiresOn()); }
        catch (java.time.format.DateTimeParseException ignored) { return LocalDate.MAX; }
    }
    private BigDecimal categoryAmount(String category, LocalDate from, LocalDate to) {
        return dashboardService.summary(from, to).categorySpending().stream()
                .filter(item -> category.equalsIgnoreCase(item.category()))
                .map(DashboardSummary.CategoryTotal::amount).findFirst().orElse(BigDecimal.ZERO);
    }
    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) { return denominator.signum() <= 0 ? null : numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP); }
    private BigDecimal precisePercent(BigDecimal numerator, BigDecimal denominator) { return denominator.signum() <= 0 ? null : numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 4, RoundingMode.DOWN); }
    private AccountContext accountContext(AccountOverviewResponse account) {
        return new AccountContext(account.id(), account.name(), account.institution(), account.accountType(), account.lastFour(),
                account.statementBalance(), account.creditLimit(), account.availableCredit(), account.creditUtilizationPercent(),
                account.currentApr(), account.promotionalApr(), account.promotionalAprExpiresOn(), account.minimumPayment(),
                account.paymentDueDate(), account.nextExpectedStatementDate(), account.cycleEndDate());
    }
    private AccountSnapshotContext snapshotContext(StatementSnapshot snapshot) {
        return new AccountSnapshotContext(snapshot.cycleEndDate(), snapshot.endingBalance(), snapshot.creditLimit(), snapshot.availableCredit(), snapshot.totalCredits(), snapshot.totalDebits());
    }

    public record Period(LocalDate from, LocalDate to, DashboardSummary summary) {}
    public record PeriodComparison(Period selectedPeriod, Period comparisonPeriod) {}
    public record CategoryPeriod(LocalDate from, LocalDate to, BigDecimal amount) {}
    public record MerchantPeriodTotal(String merchant, BigDecimal selectedAmount, BigDecimal comparisonAmount) {}
    public record CategoryComparison(String category, CategoryPeriod selectedPeriod, CategoryPeriod comparisonPeriod,
                                     List<MerchantPeriodTotal> merchants) {}
    public record CreditUtilization(BigDecimal totalLimit, BigDecimal utilized, BigDecimal available, BigDecimal utilizationPercent,
                                    BigDecimal previousUtilizationPercent, int cardCount, int cardsWithPreviousSnapshot, List<CardUtilization> cards) {}
    public record CardUtilization(String accountName, String lastFour, BigDecimal statementBalance, BigDecimal creditLimit,
                                  BigDecimal availableCredit, BigDecimal utilizationPercent, BigDecimal previousUtilizationPercent) {}
    public record CreditPaydownPlan(boolean planningAvailable, String status, BigDecimal targetUtilizationPercent,
                                    BigDecimal totalLimit, BigDecimal currentUtilized, BigDecimal currentUtilizationPercent,
                                    BigDecimal maximumUtilizedBelowTarget, BigDecimal requiredPayment,
                                    BigDecimal projectedUtilizationPercent, List<CardPaydownPriority> cardPriorities) {}
    public record CardPaydownPriority(String accountName, String lastFour, BigDecimal currentBalance, BigDecimal creditLimit,
                                      BigDecimal utilizationPercent, BigDecimal currentApr, BigDecimal promotionalApr,
                                      String promotionalAprExpiresOn, BigDecimal suggestedPayment, String priorityReason) {}
    public record AccountContext(String id, String name, String institution, AccountType accountType, String lastFour,
                                 BigDecimal statementBalance, BigDecimal creditLimit, BigDecimal availableCredit, BigDecimal creditUtilizationPercent,
                                 BigDecimal currentApr, BigDecimal promotionalApr, String promotionalAprExpiresOn, BigDecimal minimumPayment,
                                 LocalDate paymentDueDate, LocalDate nextExpectedStatementDate, LocalDate statementAsOf) {}
    public record AccountSnapshotContext(LocalDate statementAsOf, BigDecimal balance, BigDecimal creditLimit,
                                         BigDecimal availableCredit, BigDecimal totalCredits, BigDecimal totalDebits) {}
    public record AccountHistoryContext(AccountContext account, List<AccountSnapshotContext> statements) {}
}
