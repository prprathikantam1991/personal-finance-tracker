package com.pradeep.finance.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FinanceOwnerFilterTest {

    @AfterEach
    void clearOwnerContext() {
        FinanceOwnerContext.clear();
    }

    @Test
    void rejectsRequestsWithoutAFinanceSession() throws Exception {
        FinanceOwnerFilter filter = new FinanceOwnerFilter("finance_session", provider(null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(new MockHttpServletRequest("GET", "/api/accounts"), response,
                (request, servletResponse) -> {
                    throw new AssertionError("The request must not reach the application without a session.");
                });

        assertEquals(401, response.getStatus());
    }

    @Test
    void establishesAndClearsOwnerForAVerifiedFinanceSession() throws Exception {
        FinanceSessionService sessionService = mock(FinanceSessionService.class);
        when(sessionService.findValid("session-id")).thenReturn(Optional.of(
                new FinanceSession("session-id", "owner-123", Instant.now().plusSeconds(60))));
        FinanceOwnerFilter filter = new FinanceOwnerFilter("finance_session", provider(sessionService));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
        request.setCookies(new Cookie("finance_session", "session-id"));
        AtomicReference<String> ownerSeenByApplication = new AtomicReference<>();

        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> ownerSeenByApplication.set(FinanceOwnerContext.requireOwnerUserId()));

        assertEquals("owner-123", ownerSeenByApplication.get());
        assertThrows(IllegalStateException.class, FinanceOwnerContext::requireOwnerUserId);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<FinanceSessionService> provider(FinanceSessionService sessionService) {
        ObjectProvider<FinanceSessionService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sessionService);
        return provider;
    }
}
