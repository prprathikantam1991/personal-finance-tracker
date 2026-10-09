package com.pradeep.finance.assistant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.pradeep.finance.dashboard.DashboardSummary;
import com.pradeep.finance.dashboard.MerchantSpending;
import com.pradeep.finance.recurring.RecurringTransaction;
import com.pradeep.finance.transaction.TransactionResponse;

/**
 * Spring AI-facing allow-list for assistant finance lookups.
 *
 * <p>This facade deliberately delegates to {@link FinanceToolsService}; it does
 * not access repositories, statements, files, or write operations directly.
 * Keeping the boundary separate lets the V6 runtime be introduced without
 * changing the already-tested calculation and data-safety rules.</p>
 */
@Component
public class SpringAiFinanceTools {
    private final FinanceToolsService financeTools;
    private final ToolExecutionTrace toolTrace;

    public SpringAiFinanceTools(FinanceToolsService financeTools, ToolExecutionTrace toolTrace) {
        this.financeTools = financeTools;
        this.toolTrace = toolTrace;
    }

    @Tool(name = "get_monthly_summary", description = "Get confirmed income, expenses, India remittance, net cash flow, and category totals for an inclusive date range.")
    public DashboardSummary monthlySummary(
            @ToolParam(description = "Inclusive start date in ISO format: YYYY-MM-DD.") LocalDate from,
            @ToolParam(description = "Inclusive end date in ISO format: YYYY-MM-DD.") LocalDate to) {
        validateDateRange(from, to); toolTrace.record("get_monthly_summary"); return financeTools.monthlySummary(from, to);
    }

    @Tool(name = "get_category_spending", description = "Get confirmed spending totals grouped by category for an inclusive date range.")
    public List<DashboardSummary.CategoryTotal> categorySpending(
            @ToolParam(description = "Inclusive start date in ISO format: YYYY-MM-DD.") LocalDate from,
            @ToolParam(description = "Inclusive end date in ISO format: YYYY-MM-DD.") LocalDate to) {
        validateDateRange(from, to); toolTrace.record("get_category_spending"); return financeTools.categorySpending(from, to);
    }

    @Tool(name = "get_merchant_spending", description = "Get the top confirmed merchants and their spending for an inclusive date range.")
    public List<MerchantSpending> merchantSpending(
            @ToolParam(description = "Inclusive start date in ISO format: YYYY-MM-DD.") LocalDate from,
            @ToolParam(description = "Inclusive end date in ISO format: YYYY-MM-DD.") LocalDate to) {
        validateDateRange(from, to); toolTrace.record("get_merchant_spending"); return financeTools.merchantSpending(from, to);
    }

    @Tool(name = "get_recurring_activity", description = "Get detected recurring confirmed spending and income patterns. This is a read-only analysis, not a payment action.")
    public List<RecurringTransaction> recurringActivity() {
        toolTrace.record("get_recurring_activity"); return financeTools.recurringActivity();
    }

    @Tool(name = "get_account_overview", description = "Get compact saved account facts such as balances, card limits, utilization, due dates, and next statement dates.")
    public List<FinanceToolsService.AccountContext> accountOverview() {
        toolTrace.record("get_account_overview"); return financeTools.accountOverview();
    }

    @Tool(name = "get_account_history", description = "Get up to the most recent twelve saved statement snapshots for one account ID returned by get_account_overview.")
    public FinanceToolsService.AccountHistoryContext accountHistory(
            @ToolParam(description = "The exact account ID returned by get_account_overview.") String accountId) {
        toolTrace.record("get_account_history"); return financeTools.accountHistory(accountId);
    }

    @Tool(name = "compare_periods", description = "Compare confirmed finance summaries for two inclusive date ranges.")
    public FinanceToolsService.PeriodComparison comparePeriods(
            @ToolParam(description = "Selected period inclusive start date: YYYY-MM-DD.") LocalDate from,
            @ToolParam(description = "Selected period inclusive end date: YYYY-MM-DD.") LocalDate to,
            @ToolParam(description = "Comparison period inclusive start date: YYYY-MM-DD.") LocalDate compareFrom,
            @ToolParam(description = "Comparison period inclusive end date: YYYY-MM-DD.") LocalDate compareTo) {
        validateDateRange(from, to); validateDateRange(compareFrom, compareTo); toolTrace.record("compare_periods"); return financeTools.comparePeriods(from, to, compareFrom, compareTo);
    }

    @Tool(name = "compare_category_spending", description = "Compare a single spending category across two inclusive date ranges and list the top merchant changes.")
    public FinanceToolsService.CategoryComparison compareCategorySpending(
            @ToolParam(description = "Exact finance category name, such as Groceries.") String category,
            @ToolParam(description = "Selected period inclusive start date: YYYY-MM-DD.") LocalDate from,
            @ToolParam(description = "Selected period inclusive end date: YYYY-MM-DD.") LocalDate to,
            @ToolParam(description = "Comparison period inclusive start date: YYYY-MM-DD.") LocalDate compareFrom,
            @ToolParam(description = "Comparison period inclusive end date: YYYY-MM-DD.") LocalDate compareTo) {
        validateDateRange(from, to); validateDateRange(compareFrom, compareTo); toolTrace.record("compare_category_spending"); return financeTools.compareCategory(category, from, to, compareFrom, compareTo);
    }

    @Tool(name = "get_credit_utilization", description = "Get total and per-card saved credit utilization, limits, balances, available credit, and prior-statement comparison.")
    public FinanceToolsService.CreditUtilization creditUtilization() {
        toolTrace.record("get_credit_utilization"); return financeTools.creditUtilization();
    }

    @Tool(name = "get_credit_paydown_plan", description = "Calculate the payment amount required to bring total saved credit utilization strictly below a requested percentage. This never makes a payment.")
    public FinanceToolsService.CreditPaydownPlan creditPaydownPlan(
            @ToolParam(description = "Requested overall utilization percentage greater than 0 and no more than 100, for example 10.") BigDecimal targetUtilizationPercent) {
        toolTrace.record("get_credit_paydown_plan"); return financeTools.creditPaydownPlan(targetUtilizationPercent);
    }

    @Tool(name = "search_transactions", description = "Search up to 200 confirmed transactions by optional account, inclusive dates, category, or merchant. Use narrow filters to keep results small.")
    public List<TransactionResponse> searchTransactions(
            @ToolParam(description = "Optional exact account ID returned by get_account_overview.", required = false) String accountId,
            @ToolParam(description = "Optional inclusive start date: YYYY-MM-DD.", required = false) LocalDate from,
            @ToolParam(description = "Optional inclusive end date: YYYY-MM-DD.", required = false) LocalDate to,
            @ToolParam(description = "Optional exact category name.", required = false) String category,
            @ToolParam(description = "Optional merchant text to match.", required = false) String merchant) {
        if (from != null || to != null) validateDateRange(from, to); toolTrace.record("search_transactions"); return financeTools.searchTransactions(accountId, from, to, category, merchant);
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 731) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assistant date range must be between zero and 731 days.");
        }
    }
}
