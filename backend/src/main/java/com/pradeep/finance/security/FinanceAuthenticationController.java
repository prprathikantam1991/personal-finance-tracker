package com.pradeep.finance.security;

import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Browser entry point for the one-time Auth Service handoff. */
@Controller
@RequestMapping("/api/auth")
public class FinanceAuthenticationController {
    private final AuthServiceHandoffClient handoffClient;
    private final FinanceSessionService sessions;
    private final LegacyOwnerClaimService legacyOwnerClaimService;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String allowedReturnUrl;

    public FinanceAuthenticationController(
            AuthServiceHandoffClient handoffClient,
            FinanceSessionService sessions,
            LegacyOwnerClaimService legacyOwnerClaimService,
            @Value("${finance.security.session.cookie-name:finance_session}") String cookieName,
            @Value("${finance.security.session.cookie-secure:false}") boolean cookieSecure,
            @Value("${finance.security.handoff.allowed-return-url:http://localhost:4200/dashboard}") String allowedReturnUrl) {
        this.handoffClient = handoffClient;
        this.sessions = sessions;
        this.legacyOwnerClaimService = legacyOwnerClaimService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.allowedReturnUrl = allowedReturnUrl;
    }

    @GetMapping("/handoff/callback")
    public String completeHandoff(@RequestParam(required = false) String code,
            @RequestParam(required = false) String returnUrl, HttpServletResponse response) {
        Optional<String> ownerUserId = handoffClient.exchange(code);
        if (ownerUserId.isEmpty()) return "redirect:" + allowedReturnUrl + "?signIn=failed";

        legacyOwnerClaimService.claimIfAvailable(ownerUserId.get());
        FinanceSession session = sessions.create(ownerUserId.get());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(cookieName, session.id())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.between(java.time.Instant.now(), session.expiresAt()))
                .build().toString());
        return "redirect:" + safeReturnUrl(returnUrl);
    }

    private String safeReturnUrl(String returnUrl) {
        return allowedReturnUrl.equals(returnUrl) ? returnUrl : allowedReturnUrl;
    }
}
