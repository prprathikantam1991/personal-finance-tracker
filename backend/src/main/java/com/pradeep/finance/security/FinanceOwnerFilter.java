package com.pradeep.finance.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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

    public FinanceOwnerFilter(@Value("${finance.security.local-owner-id:local-owner}") String localOwnerUserId) {
        this.localOwnerUserId = localOwnerUserId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        FinanceOwnerContext.set(localOwnerUserId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            FinanceOwnerContext.clear();
        }
    }
}
