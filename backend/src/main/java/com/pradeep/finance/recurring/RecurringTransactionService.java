package com.pradeep.finance.recurring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RecurringTransactionService {
    private final JdbcTemplate jdbcTemplate;

    public RecurringTransactionService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<RecurringTransaction> findRecurringTransactions() {
        List<Candidate> rows = jdbcTemplate.query("""
                SELECT transaction_date, description, amount, category
                FROM transactions
                WHERE status = 'CONFIRMED' AND category NOT IN ('Transfer', 'Uncategorized')
                ORDER BY transaction_date
                """, (rs, row) -> new Candidate(LocalDate.parse(rs.getString("transaction_date")),
                rs.getString("description"), rs.getBigDecimal("amount"), rs.getString("category")));

        Map<String, List<Candidate>> groups = rows.stream().collect(Collectors.groupingBy(
                row -> normalizeMerchant(row.description()) + "|" + row.category(), LinkedHashMap::new, Collectors.toList()));
        return groups.values().stream().map(this::toRecurring).flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(RecurringTransaction::nextExpectedDate).thenComparing(RecurringTransaction::merchant))
                .toList();
    }

    private java.util.Optional<RecurringTransaction> toRecurring(List<Candidate> group) {
        if (group.size() < 3) return java.util.Optional.empty();
        List<Candidate> ordered = group.stream().sorted(Comparator.comparing(Candidate::date)).toList();
        List<Long> gaps = new ArrayList<>();
        for (int index = 1; index < ordered.size(); index++) gaps.add(ChronoUnit.DAYS.between(ordered.get(index - 1).date(), ordered.get(index).date()));
        long averageGap = Math.round(gaps.stream().mapToLong(Long::longValue).average().orElse(0));
        if (averageGap < 24 || averageGap > 40 || gaps.stream().anyMatch(gap -> gap < 18 || gap > 45)) return java.util.Optional.empty();

        BigDecimal averageAmount = ordered.stream().map(Candidate::amount).map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(ordered.size()), 2, RoundingMode.HALF_UP);
        BigDecimal tolerance = averageAmount.multiply(BigDecimal.valueOf(0.15)).max(BigDecimal.valueOf(2));
        if (ordered.stream().anyMatch(item -> item.amount().abs().subtract(averageAmount).abs().compareTo(tolerance) > 0)) return java.util.Optional.empty();

        Candidate latest = ordered.get(ordered.size() - 1);
        return java.util.Optional.of(new RecurringTransaction(normalizeMerchant(latest.description()), latest.category(), averageAmount,
                "Monthly", ordered.size(), latest.date(), latest.date().plusDays(averageGap)));
    }

    private String normalizeMerchant(String description) {
        return description.toUpperCase(Locale.ROOT)
                .replaceAll("\\b(?:ACH|DEBIT|CREDIT|CARD|PAYMENT|PURCHASE|WITHDRAWAL)\\b", "")
                .replaceAll("\\b(?:REF|CONFIRMATION|TRACE|ID|#)\\s*[A-Z0-9-]+", "")
                .replaceAll("\\b\\d{4,}\\b", "")
                .replaceAll("[^A-Z ]", " ").replaceAll("\\s+", " ").trim();
    }

    private record Candidate(LocalDate date, String description, BigDecimal amount, String category) {}
}
