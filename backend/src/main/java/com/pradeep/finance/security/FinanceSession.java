package com.pradeep.finance.security;

import java.time.Instant;

/** Server-side session data. Only the opaque session ID is stored in the browser cookie. */
public record FinanceSession(String id, String ownerUserId, Instant expiresAt) {
}
