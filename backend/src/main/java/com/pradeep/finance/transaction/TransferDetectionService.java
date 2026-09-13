package com.pradeep.finance.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferDetectionService {
    private final JdbcTemplate jdbcTemplate;

    public TransferDetectionService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @EventListener(ApplicationReadyEvent.class)
    public void detectAtStartup() { detectTransfers(); }

    @Transactional
    public int detectTransfers() {
        List<Candidate> candidates = jdbcTemplate.query("""
                SELECT t.id, t.account_id, a.account_type, t.transaction_date, t.amount
                FROM transactions t JOIN accounts a ON a.id = t.account_id
                WHERE t.transfer_group_id IS NULL AND t.category = 'Transfer'
                ORDER BY t.transaction_date, t.id
                """, (rs, row) -> new Candidate(rs.getString(1), rs.getString(2), rs.getString(3), LocalDate.parse(rs.getString(4)), rs.getBigDecimal(5)));
        boolean[] linked = new boolean[candidates.size()];
        int links = 0;
        for (int left = 0; left < candidates.size(); left++) {
            if (linked[left]) continue;
            for (int right = left + 1; right < candidates.size(); right++) {
                if (linked[right] || !matches(candidates.get(left), candidates.get(right))) continue;
                String groupId = UUID.randomUUID().toString();
                jdbcTemplate.update("UPDATE transactions SET transfer_group_id = ? WHERE id IN (?, ?)", groupId, candidates.get(left).id(), candidates.get(right).id());
                linked[left] = true;
                linked[right] = true;
                links++;
                break;
            }
        }
        return links;
    }

    private boolean matches(Candidate left, Candidate right) {
        if (left.accountId().equals(right.accountId())
                || Math.abs(ChronoUnit.DAYS.between(left.date(), right.date())) > 5) return false;

        boolean oppositeSigns = left.amount().add(right.amount()).compareTo(BigDecimal.ZERO) == 0;
        boolean cardPaymentWithSameSign = left.amount().abs().compareTo(right.amount().abs()) == 0
                && ("CREDIT_CARD".equals(left.accountType()) ^ "CREDIT_CARD".equals(right.accountType()));
        return oppositeSigns || cardPaymentWithSameSign;
    }

    private record Candidate(String id, String accountId, String accountType, LocalDate date, BigDecimal amount) { }
}
