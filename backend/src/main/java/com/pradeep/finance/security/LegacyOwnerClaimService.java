package com.pradeep.finance.security;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moves the pre-authentication local ledger to the first verified Finance owner exactly once. */
@Service
public class LegacyOwnerClaimService {
    private static final String LEGACY_OWNER = "local-owner";
    private final JdbcTemplate jdbcTemplate;

    public LegacyOwnerClaimService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean claimIfAvailable(String authenticatedOwnerUserId) {
        int claimed = jdbcTemplate.update("""
                UPDATE finance_owner_bootstrap
                SET claimed_owner_user_id = ?, claimed_at = ?
                WHERE id = 1 AND claimed_owner_user_id IS NULL
                """, authenticatedOwnerUserId, Instant.now().toString());
        if (claimed == 0) return false;

        jdbcTemplate.update("UPDATE accounts SET owner_user_id = ? WHERE owner_user_id = ?", authenticatedOwnerUserId, LEGACY_OWNER);
        jdbcTemplate.update("UPDATE statement_imports SET owner_user_id = ? WHERE owner_user_id = ?", authenticatedOwnerUserId, LEGACY_OWNER);
        jdbcTemplate.update("UPDATE transactions SET owner_user_id = ? WHERE owner_user_id = ?", authenticatedOwnerUserId, LEGACY_OWNER);
        jdbcTemplate.update("UPDATE category_rules SET owner_user_id = ? WHERE owner_user_id = ?", authenticatedOwnerUserId, LEGACY_OWNER);
        jdbcTemplate.update("UPDATE assistant_conversations SET owner_user_id = ? WHERE owner_user_id = ?", authenticatedOwnerUserId, LEGACY_OWNER);
        return true;
    }
}
