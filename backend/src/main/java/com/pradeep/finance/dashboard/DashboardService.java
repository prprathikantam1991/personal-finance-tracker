package com.pradeep.finance.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.pradeep.finance.transaction.MerchantNormalizer;

@Service
public class DashboardService {
    private final JdbcTemplate jdbcTemplate;
    private final MerchantNormalizer merchantNormalizer;
    public DashboardService(JdbcTemplate jdbcTemplate, MerchantNormalizer merchantNormalizer) { this.jdbcTemplate = jdbcTemplate; this.merchantNormalizer = merchantNormalizer; }

    public DashboardSummary summary(LocalDate from, LocalDate to) {
        String dateFilter = " AND (? IS NULL OR t.transaction_date >= ?) AND (? IS NULL OR t.transaction_date <= ?)";
        Object[] dates = { from == null ? null : from.toString(), from == null ? null : from.toString(), to == null ? null : to.toString(), to == null ? null : to.toString() };
        BigDecimal income = amount("SELECT COALESCE(SUM(CASE WHEN a.account_type <> 'CREDIT_CARD' AND t.amount > 0 AND t.category = 'Income' THEN t.amount ELSE 0 END), 0) FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.status='CONFIRMED'" + dateFilter, dates);
        BigDecimal expenses = amount("SELECT COALESCE(SUM(CASE WHEN t.category NOT IN ('Income','Transfer','India Remittance') AND a.account_type = 'CREDIT_CARD' THEN t.amount WHEN t.category NOT IN ('Income','Transfer','India Remittance') AND a.account_type <> 'CREDIT_CARD' THEN -t.amount ELSE 0 END), 0) FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.status='CONFIRMED'" + dateFilter, dates);
        BigDecimal remittance = amount("SELECT COALESCE(SUM(CASE WHEN a.account_type = 'CREDIT_CARD' THEN t.amount ELSE -t.amount END), 0) FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.status='CONFIRMED' AND t.category='India Remittance'" + dateFilter, dates);
        Integer review = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions WHERE status <> 'CONFIRMED' OR category = 'Uncategorized'", Integer.class);
        List<DashboardSummary.CategoryTotal> categories = jdbcTemplate.query("SELECT t.category, SUM(CASE WHEN a.account_type='CREDIT_CARD' THEN t.amount ELSE -t.amount END) total FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.status='CONFIRMED' AND t.category NOT IN ('Income','Transfer','India Remittance')" + dateFilter + " GROUP BY t.category HAVING total <> 0 ORDER BY total DESC", (rs, row) -> new DashboardSummary.CategoryTotal(rs.getString(1), rs.getBigDecimal(2)), dates);
        return new DashboardSummary(income, expenses, remittance, income.subtract(expenses).subtract(remittance), review == null ? 0 : review, categories);
    }

    public List<MonthlyTrend> monthlyTrends(LocalDate through, int months) {
        int requestedMonths = Math.clamp(months, 3, 12);
        YearMonth endMonth = YearMonth.from(through == null ? LocalDate.now() : through);
        YearMonth startMonth = endMonth.minusMonths(requestedMonths - 1);
        Map<String, MonthlyTrend> byMonth = new HashMap<>();
        jdbcTemplate.query("""
                SELECT substr(t.transaction_date, 1, 7) AS month,
                       COALESCE(SUM(CASE WHEN a.account_type <> 'CREDIT_CARD' AND t.amount > 0 AND t.category = 'Income' THEN t.amount ELSE 0 END), 0) AS income,
                       COALESCE(SUM(CASE WHEN t.category NOT IN ('Income','Transfer','India Remittance') AND a.account_type = 'CREDIT_CARD' THEN t.amount WHEN t.category NOT IN ('Income','Transfer','India Remittance') AND a.account_type <> 'CREDIT_CARD' THEN -t.amount ELSE 0 END), 0) AS expenses,
                       COALESCE(SUM(CASE WHEN t.category = 'India Remittance' AND a.account_type = 'CREDIT_CARD' THEN t.amount WHEN t.category = 'India Remittance' THEN -t.amount ELSE 0 END), 0) AS remittance
                FROM transactions t LEFT JOIN accounts a ON a.id = t.account_id
                WHERE t.status = 'CONFIRMED' AND t.transaction_date >= ? AND t.transaction_date <= ?
                GROUP BY substr(t.transaction_date, 1, 7)
                """, rs -> {
            BigDecimal income = rs.getBigDecimal("income");
            BigDecimal expenses = rs.getBigDecimal("expenses");
            BigDecimal remittance = rs.getBigDecimal("remittance");
            byMonth.put(rs.getString("month"), new MonthlyTrend(rs.getString("month"), income, expenses, remittance, income.subtract(expenses).subtract(remittance)));
        }, startMonth.atDay(1).toString(), endMonth.atEndOfMonth().toString());

        return java.util.stream.IntStream.range(0, requestedMonths)
                .mapToObj(startMonth::plusMonths)
                .map(month -> byMonth.getOrDefault(month.toString(), new MonthlyTrend(month.toString(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)))
                .toList();
    }
    public List<MerchantSpending> merchantSpending(LocalDate from, LocalDate to) {
        Map<String, MerchantTotal> current = merchantTotals(from, to);
        Map<String, MerchantTotal> previous = Map.of();
        if (from != null && to != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
            LocalDate previousEnd = from.minusDays(1);
            previous = merchantTotals(previousEnd.minusDays(days - 1), previousEnd);
        }
        Map<String, MerchantTotal> previousTotals = previous;
        return current.entrySet().stream().map(entry -> {
            MerchantTotal total = entry.getValue();
            return new MerchantSpending(entry.getKey(), total.category(), total.amount(), total.count(), previousTotals.getOrDefault(entry.getKey(), MerchantTotal.empty()).amount());
        }).sorted(java.util.Comparator.comparing(MerchantSpending::amount).reversed()).limit(5).toList();
    }
    private Map<String, MerchantTotal> merchantTotals(LocalDate from, LocalDate to) {
        Map<String, MerchantTotal> totals = new HashMap<>();
        jdbcTemplate.query("""
                SELECT t.description, t.category, t.amount, a.account_type FROM transactions t
                LEFT JOIN accounts a ON a.id = t.account_id
                WHERE t.status='CONFIRMED' AND t.category NOT IN ('Income','Transfer','India Remittance')
                AND (? IS NULL OR t.transaction_date >= ?) AND (? IS NULL OR t.transaction_date <= ?)
                """, rs -> {
            String merchant = merchantNormalizer.normalize(rs.getString("description"));
            BigDecimal amount = rs.getBigDecimal("amount");
            BigDecimal spend = "CREDIT_CARD".equals(rs.getString("account_type")) ? amount : amount.negate();
            if (spend.signum() <= 0) return;
            MerchantTotal existing = totals.getOrDefault(merchant, MerchantTotal.empty());
            totals.put(merchant, new MerchantTotal(rs.getString("category"), existing.amount().add(spend), existing.count() + 1));
        }, from == null ? null : from.toString(), from == null ? null : from.toString(), to == null ? null : to.toString(), to == null ? null : to.toString());
        return totals;
    }
    private record MerchantTotal(String category, BigDecimal amount, int count) { static MerchantTotal empty() { return new MerchantTotal("", BigDecimal.ZERO, 0); } }
    private BigDecimal amount(String sql, Object[] parameters) { BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, parameters); return value == null ? BigDecimal.ZERO : value; }
}
