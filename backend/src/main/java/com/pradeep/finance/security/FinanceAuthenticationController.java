package com.pradeep.finance.security;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Browser entry point for the one-time Auth Service handoff. */
@Controller
@RequestMapping("/api/auth")
public class FinanceAuthenticationController {
    private static final Logger log = LoggerFactory.getLogger(FinanceAuthenticationController.class);
    private final AuthServiceHandoffClient handoffClient;
    private final FinanceSessionService sessions;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String allowedReturnUrl;

    public FinanceAuthenticationController(
            AuthServiceHandoffClient handoffClient,
            FinanceSessionService sessions,
            @Value("${finance.security.session.cookie-name:finance_session}") String cookieName,
            @Value("${finance.security.session.cookie-secure:false}") boolean cookieSecure,
            @Value("${finance.security.handoff.allowed-return-url:http://localhost:4200/dashboard}") String allowedReturnUrl) {
        this.handoffClient = handoffClient;
        this.sessions = sessions;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.allowedReturnUrl = allowedReturnUrl;
    }

    @GetMapping("/handoff/callback")
    public String completeHandoff(@RequestParam(required = false) String code,
            @RequestParam(required = false) String returnUrl, HttpServletResponse response) {
        Optional<String> ownerUserId = handoffClient.exchange(code);
        if (ownerUserId.isEmpty()) return "redirect:" + allowedReturnUrl + "?signIn=failed";

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

    @GetMapping("/session")
    @ResponseBody
    public Map<String, Boolean> session(jakarta.servlet.http.HttpServletRequest request) {
        boolean authenticated = sessionCookie(request)
                .flatMap(sessions::findValid)
                .isPresent();
        return Map.of("authenticationEnabled", true, "authenticated", authenticated);
    }

    @PostMapping("/logout")
    @ResponseBody
    public Map<String, Boolean> logout(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        boolean invalidated = sessionCookie(request).map(sessions::invalidate).orElse(false);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(cookieName, "")
                .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(Duration.ZERO).build().toString());
        log.info("Finance logout completed; server session invalidated={}", invalidated);
        return Map.of("signedOut", true);
    }

    private String safeReturnUrl(String returnUrl) {
        return allowedReturnUrl.equals(returnUrl) ? returnUrl : allowedReturnUrl;
    }

    private Optional<String> sessionCookie(jakarta.servlet.http.HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) return Optional.of(cookie.getValue());
        }
        return Optional.empty();
    }
}
