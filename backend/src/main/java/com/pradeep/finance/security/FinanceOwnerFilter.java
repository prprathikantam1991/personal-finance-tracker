package com.pradeep.finance.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Transitional V7 owner source for the existing local-first single-user app.
 * It never trusts a client-supplied owner value. The future Finance session
 * filter will establish the same context from a verified Auth Service handoff.
 */
@Component
public class FinanceOwnerFilter extends OncePerRequestFilter {
    private final String localOwnerUserId;
    private final boolean authenticationEnabled;
    private final String cookieName;
    private final ObjectProvider<FinanceSessionService> sessions;

    public FinanceOwnerFilter(
            @Value("${finance.security.local-owner-id:local-owner}") String localOwnerUserId,
            @Value("${finance.security.authentication-enabled:false}") boolean authenticationEnabled,
            @Value("${finance.security.session.cookie-name:finance_session}") String cookieName,
            ObjectProvider<FinanceSessionService> sessions) {
        this.localOwnerUserId = localOwnerUserId;
        this.authenticationEnabled = authenticationEnabled;
        this.cookieName = cookieName;
        this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/") || uri.equals("/api/health") || uri.equals("/api/auth/handoff/callback")
                || uri.equals("/api/auth/session") || uri.equals("/api/auth/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ownerUserId = authenticationEnabled ? authenticatedOwner(request) : localOwnerUserId;
        if (ownerUserId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sign in is required.");
            return;
        }
        FinanceOwnerContext.set(ownerUserId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            FinanceOwnerContext.clear();
        }
    }

    private String authenticatedOwner(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                FinanceSessionService sessionService = sessions.getIfAvailable();
                return sessionService == null ? null
                        : sessionService.findValid(cookie.getValue()).map(FinanceSession::ownerUserId).orElse(null);
            }
        }
        return null;
    }
}
