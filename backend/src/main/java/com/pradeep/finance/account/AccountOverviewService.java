package com.pradeep.finance.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AccountOverviewService {
    private final JdbcTemplate jdbcTemplate;
    public AccountOverviewService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    public List<AccountOverviewResponse> list() {
        return jdbcTemplate.query("""
                SELECT a.*, COALESCE(si.credit_limit, a.credit_limit) AS effective_credit_limit, si.statement_balance, si.available_credit,
                (SELECT x.statement_balance FROM statement_imports x WHERE x.account_id = a.id AND x.id <> si.id ORDER BY COALESCE(x.cycle_end_date, x.imported_at) DESC LIMIT 1) AS previous_statement_balance,
                (SELECT COALESCE(x.credit_limit, a.credit_limit) FROM statement_imports x WHERE x.account_id = a.id AND x.id <> si.id ORDER BY COALESCE(x.cycle_end_date, x.imported_at) DESC LIMIT 1) AS previous_credit_limit,
                si.fees_charged, si.interest_charged, si.minimum_payment,
                si.payment_due_date, si.cycle_start_date, si.cycle_end_date, si.beginning_balance, si.total_credits, si.total_debits,
                si.interest_earned, si.annual_interest_rate, si.annual_percentage_yield, si.imported_at AS statement_imported_at
                FROM accounts a LEFT JOIN statement_imports si ON si.id = (
                  SELECT id FROM statement_imports x WHERE x.account_id = a.id ORDER BY COALESCE(x.cycle_end_date, x.imported_at) DESC LIMIT 1)
                ORDER BY a.institution, a.name
                """, (rs, row) -> new AccountOverviewResponse(
                rs.getString("id"), rs.getString("name"), rs.getString("institution"), AccountType.valueOf(rs.getString("account_type")), rs.getString("last_four"), rs.getString("currency"), Instant.parse(rs.getString("created_at")),
                rs.getBigDecimal("current_apr"), rs.getBigDecimal("promotional_apr"), rs.getString("promotional_apr_expires_on"), rs.getBigDecimal("effective_credit_limit"), rs.getBigDecimal("penalty_apr"),
                rs.getBigDecimal("statement_balance"), rs.getBigDecimal("available_credit"),
                utilization(rs.getBigDecimal("statement_balance"), rs.getBigDecimal("effective_credit_limit")),
                utilization(rs.getBigDecimal("previous_statement_balance"), rs.getBigDecimal("previous_credit_limit")),
                rs.getBigDecimal("previous_statement_balance"), rs.getBigDecimal("previous_credit_limit"),
                rs.getBigDecimal("fees_charged"), rs.getBigDecimal("interest_charged"), rs.getBigDecimal("minimum_payment"),
                localDate(rs.getString("payment_due_date")), localDate(rs.getString("cycle_start_date")), localDate(rs.getString("cycle_end_date")), nextStatementDate(localDate(rs.getString("cycle_end_date"))),
                rs.getBigDecimal("beginning_balance"), rs.getBigDecimal("total_credits"), rs.getBigDecimal("total_debits"), rs.getBigDecimal("interest_earned"), rs.getBigDecimal("annual_interest_rate"), rs.getBigDecimal("annual_percentage_yield"),
                instant(rs.getString("statement_imported_at"))));
    }
    public AccountHistoryResponse history(String accountId) {
        AccountOverviewResponse account = list().stream().filter(item -> item.id().equals(accountId)).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Account not found."));
        List<StatementSnapshot> snapshots = jdbcTemplate.query("""
                SELECT cycle_start_date, cycle_end_date, beginning_balance, statement_balance, total_credits, total_debits,
                interest_earned, annual_interest_rate, annual_percentage_yield, credit_limit, available_credit,
                fees_charged, interest_charged, imported_at
                FROM statement_imports WHERE account_id = ?
                ORDER BY COALESCE(cycle_end_date, imported_at)
                """, (rs, row) -> new StatementSnapshot(localDate(rs.getString(1)), localDate(rs.getString(2)), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getBigDecimal(12), rs.getBigDecimal(13), instant(rs.getString(14))), accountId);
        return new AccountHistoryResponse(account, snapshots);
    }
    private LocalDate localDate(String value) { return value == null ? null : LocalDate.parse(value); }
    private LocalDate nextStatementDate(LocalDate cycleEndDate) { return cycleEndDate == null ? null : cycleEndDate.plusMonths(1); }
    private Instant instant(String value) { return value == null ? null : Instant.parse(value); }
    private BigDecimal utilization(BigDecimal balance, BigDecimal limit) {
        if (balance == null || limit == null || limit.signum() <= 0) return null;
        return balance.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP);
    }
}
