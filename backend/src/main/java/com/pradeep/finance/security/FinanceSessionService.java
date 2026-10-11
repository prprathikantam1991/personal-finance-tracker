package com.pradeep.finance.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FinanceSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final Duration sessionLifetime;

    public FinanceSessionService(
            JdbcTemplate jdbcTemplate,
            @Value("${finance.security.session.max-age:PT8H}") Duration sessionLifetime) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionLifetime = sessionLifetime;
    }

    public FinanceSession create(String ownerUserId) {
        Instant now = Instant.now();
        FinanceSession session = new FinanceSession(newSessionId(), ownerUserId, now.plus(sessionLifetime));
        jdbcTemplate.update("""
                INSERT INTO finance_sessions (id, owner_user_id, created_at, last_seen_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, session.id(), session.ownerUserId(), now.toString(), now.toString(), session.expiresAt().toString());
        return session;
    }

    public Optional<FinanceSession> findValid(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        Instant now = Instant.now();
        jdbcTemplate.update("DELETE FROM finance_sessions WHERE expires_at <= ?", now.toString());
        return jdbcTemplate.query("""
                        SELECT id, owner_user_id, expires_at FROM finance_sessions
                        WHERE id = ? AND expires_at > ?
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(new FinanceSession(resultSet.getString("id"), resultSet.getString("owner_user_id"),
                                Instant.parse(resultSet.getString("expires_at"))))
                        : Optional.empty(),
                sessionId, now.toString());
    }

    public void invalidate(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            jdbcTemplate.update("DELETE FROM finance_sessions WHERE id = ?", sessionId);
        }
    }

    public Duration sessionLifetime() {
        return sessionLifetime;
    }

    private String newSessionId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
