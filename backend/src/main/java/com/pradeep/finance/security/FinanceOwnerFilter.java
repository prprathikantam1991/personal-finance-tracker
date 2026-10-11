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
 * Establishes the request owner exclusively from a verified Finance session.
 * It never trusts a client-supplied owner value.
 */
@Component
public class FinanceOwnerFilter extends OncePerRequestFilter {
    private final String cookieName;
    private final ObjectProvider<FinanceSessionService> sessions;

    public FinanceOwnerFilter(
            @Value("${finance.security.session.cookie-name:finance_session}") String cookieName,
            ObjectProvider<FinanceSessionService> sessions) {
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
        String ownerUserId = authenticatedOwner(request);
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
