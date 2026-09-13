package com.pradeep.finance.transaction;

import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CategoryRuleService {
    private final JdbcTemplate jdbcTemplate;
    private final MerchantNormalizer merchantNormalizer;
    public CategoryRuleService(JdbcTemplate jdbcTemplate, MerchantNormalizer merchantNormalizer) { this.jdbcTemplate = jdbcTemplate; this.merchantNormalizer = merchantNormalizer; }

    public String categoryFor(String description) {
        String category = jdbcTemplate.query("SELECT category FROM category_rules WHERE lower(match_text) = ? ORDER BY last_used_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, merchantKey(description));
        if (category == null) {
            category = jdbcTemplate.query("SELECT category FROM category_rules WHERE ? LIKE '%' || lower(match_text) || '%' ORDER BY length(match_text) DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, description.toLowerCase(java.util.Locale.ROOT));
        }
        return category;
    }

    public void remember(String description, String category) {
        String matchText = merchantKey(description);
        String now = Instant.now().toString();
        jdbcTemplate.update("""
                INSERT INTO category_rules (id, match_text, category, created_at, last_used_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(match_text) DO UPDATE SET category = excluded.category, last_used_at = excluded.last_used_at
                """, UUID.randomUUID().toString(), matchText, category, now, now);
    }

    public boolean hasMerchantRule(String description) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM category_rules WHERE lower(match_text) = ?", Integer.class, merchantKey(description));
        return count != null && count > 0;
    }

    private String merchantKey(String description) { return merchantNormalizer.key(description); }
}
