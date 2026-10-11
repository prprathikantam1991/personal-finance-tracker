package com.pradeep.finance.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Requires a session-bound synchronizer token for browser mutations authenticated by a cookie. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class FinanceCsrfFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Finance-CSRF";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod()) || request.getRequestURI().startsWith("/actuator/")
                || request.getRequestURI().equals("/api/health") || request.getRequestURI().equals("/api/auth/handoff/callback");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Object sessionValue = request.getAttribute(FinanceOwnerFilter.SESSION_REQUEST_ATTRIBUTE);
        if (!(sessionValue instanceof FinanceSession session)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sign in is required.");
            return;
        }
        String presented = request.getHeader(HEADER_NAME);
        if (presented == null || !MessageDigest.isEqual(session.csrfToken().getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "A valid CSRF token is required.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
