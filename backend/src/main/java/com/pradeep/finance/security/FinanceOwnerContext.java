package com.pradeep.finance.security;

/** Request-scoped owner identity derived from the verified Finance session principal. */
public final class FinanceOwnerContext {
    private static final ThreadLocal<String> OWNER = new ThreadLocal<>();

    private FinanceOwnerContext() {
    }

    public static void set(String ownerUserId) {
        OWNER.set(ownerUserId);
    }

    public static String requireOwnerUserId() {
        String ownerUserId = OWNER.get();
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new IllegalStateException("No authenticated finance owner is available for this request.");
        }
        return ownerUserId;
    }

    public static void clear() {
        OWNER.remove();
    }
}
