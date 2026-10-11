package com.pradeep.finance.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FinanceSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final Duration sessionLifetime;
    private final Duration idleTimeout;
    private final Duration touchInterval;

    public FinanceSessionService(
            JdbcTemplate jdbcTemplate,
            @Value("${finance.security.session.max-age:PT8H}") Duration sessionLifetime,
            @Value("${finance.security.session.idle-timeout:PT30M}") Duration idleTimeout,
            @Value("${finance.security.session.touch-interval:PT5M}") Duration touchInterval) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionLifetime = sessionLifetime;
        this.idleTimeout = idleTimeout;
        this.touchInterval = touchInterval;
    }

    public FinanceSession create(String ownerUserId) {
        Instant now = Instant.now();
        FinanceSession session = new FinanceSession(newRandomValue(), ownerUserId, newRandomValue(), now.plus(sessionLifetime));
        jdbcTemplate.update("""
                INSERT INTO finance_sessions (id, owner_user_id, csrf_token, created_at, last_seen_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, session.id(), session.ownerUserId(), session.csrfToken(), now.toString(), now.toString(), session.expiresAt().toString());
        return session;
    }

    public Optional<FinanceSession> findValid(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        Instant now = Instant.now();
        Optional<FinanceSession> session = jdbcTemplate.query("""
                        SELECT id, owner_user_id, csrf_token, expires_at FROM finance_sessions
                        WHERE id = ? AND expires_at > ? AND last_seen_at > ?
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(new FinanceSession(resultSet.getString("id"), resultSet.getString("owner_user_id"), resultSet.getString("csrf_token"),
                                Instant.parse(resultSet.getString("expires_at"))))
                        : Optional.empty(),
                sessionId, now.toString(), now.minus(idleTimeout).toString());
        session.ifPresent(value -> touchIfNeeded(value.id(), now));
        return session;
    }

    public boolean invalidate(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return jdbcTemplate.update("DELETE FROM finance_sessions WHERE id = ?", sessionId) > 0;
        }
        return false;
    }

    public Duration sessionLifetime() {
        return sessionLifetime;
    }

    @Scheduled(fixedDelayString = "${finance.security.session.cleanup-interval-ms:900000}")
    public void purgeExpired() {
        Instant now = Instant.now();
        jdbcTemplate.update("DELETE FROM finance_sessions WHERE expires_at <= ? OR last_seen_at <= ?", now.toString(), now.minus(idleTimeout).toString());
    }

    private void touchIfNeeded(String sessionId, Instant now) {
        // A session is read on every API request. Touching at most once per configured
        // interval preserves an idle timeout without turning routine dashboard reads
        // into frequent SQLite writes.
        jdbcTemplate.update("UPDATE finance_sessions SET last_seen_at = ? WHERE id = ? AND last_seen_at <= ?",
                now.toString(), sessionId, now.minus(touchInterval).toString());
    }

    private String newRandomValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
