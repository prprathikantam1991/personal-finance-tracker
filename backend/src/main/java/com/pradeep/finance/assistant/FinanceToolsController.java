package com.pradeep.finance.assistant;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.pradeep.finance.dashboard.DashboardSummary;
import com.pradeep.finance.dashboard.MerchantSpending;
import com.pradeep.finance.recurring.RecurringTransaction;
import com.pradeep.finance.transaction.TransactionResponse;

/** Deliberately read-only endpoints used by the V3 assistant orchestration layer. */
@RestController
@RequestMapping("/api/finance-tools")
public class FinanceToolsController {
    private final FinanceToolsService financeToolsService;
    public FinanceToolsController(FinanceToolsService financeToolsService) { this.financeToolsService = financeToolsService; }

    @GetMapping("/monthly-summary") public DashboardSummary monthlySummary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return financeToolsService.monthlySummary(from, to); }
    @GetMapping("/category-spending") public List<DashboardSummary.CategoryTotal> categorySpending(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return financeToolsService.categorySpending(from, to); }
    @GetMapping("/merchant-spending") public List<MerchantSpending> merchantSpending(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return financeToolsService.merchantSpending(from, to); }
    @GetMapping("/recurring-activity") public List<RecurringTransaction> recurringActivity() { return financeToolsService.recurringActivity(); }
    @GetMapping("/accounts") public List<FinanceToolsService.AccountContext> accountOverview() { return financeToolsService.accountOverview(); }
    @GetMapping("/accounts/{accountId}/history") public FinanceToolsService.AccountHistoryContext accountHistory(@PathVariable String accountId) { return financeToolsService.accountHistory(accountId); }
    @GetMapping("/credit-utilization") public FinanceToolsService.CreditUtilization creditUtilization() { return financeToolsService.creditUtilization(); }
    @GetMapping("/compare-periods") public FinanceToolsService.PeriodComparison comparePeriods(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate compareFrom, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate compareTo) { return financeToolsService.comparePeriods(from, to, compareFrom, compareTo); }
    @GetMapping("/transactions") public List<TransactionResponse> searchTransactions(@RequestParam(required = false) String accountId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) String category, @RequestParam(required = false) String merchant) { return financeToolsService.searchTransactions(accountId, from, to, category, merchant); }
}
