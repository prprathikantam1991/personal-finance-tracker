package com.pradeep.finance.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:finance-security-integration?mode=memory&cache=shared",
        "finance.automation.enabled=false",
        "finance.storage.statements-directory=target/finance-security-statements",
        "finance.security.session.cleanup-interval-ms=86400000",
        "finance.assistant.lm-studio.base-url=http://localhost:1234/v1",
        "finance.assistant.lm-studio.api-key=test",
        "finance.assistant.model=test-model",
        "spring.ai.openai.api-key=test"
})
@AutoConfigureMockMvc
class FinanceSecurityIntegrationTest {
    private static final String OWNER_A = "owner-a";
    private static final String OWNER_B = "owner-b";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FinanceSessionService sessions;

    private FinanceSession ownerASession;
    private FinanceSession ownerBSession;

    @BeforeEach
    void resetLedger() {
        jdbcTemplate.update("DELETE FROM assistant_messages");
        jdbcTemplate.update("DELETE FROM assistant_memory_summaries");
        jdbcTemplate.update("DELETE FROM assistant_conversations");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM statement_imports");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM finance_sessions");
        ownerASession = sessions.create(OWNER_A);
        ownerBSession = sessions.create(OWNER_B);
    }

    @Test
    void isolatesAccountsTransactionsImportsAndConversationsBetweenOwners() throws Exception {
        createAccount(ownerASession, "Owner A Checking");
        createAccount(ownerBSession, "Owner B Checking");

        String ownerBAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM accounts WHERE owner_user_id = ?", String.class, OWNER_B);
        jdbcTemplate.update("""
                INSERT INTO statement_imports (id, account_id, original_filename, stored_filename, source_type, status, imported_at, owner_user_id)
                VALUES ('import-b', ?, 'owner-b.csv', 'owner-b.csv', 'CSV', 'REVIEW_REQUIRED', ?, ?)
                """, ownerBAccountId, Instant.now().toString(), OWNER_B);
        jdbcTemplate.update("""
                INSERT INTO transactions (id, import_id, account_id, transaction_date, description, amount, status, created_at, category, owner_user_id)
                VALUES ('transaction-b', 'import-b', ?, '2026-10-01', 'Owner B purchase', -20.00, 'REVIEW_REQUIRED', ?, 'Uncategorized', ?)
                """, ownerBAccountId, Instant.now().toString(), OWNER_B);
        jdbcTemplate.update("""
                INSERT INTO assistant_conversations (id, created_at, last_active_at, title, owner_user_id)
                VALUES ('conversation-b', ?, ?, 'Owner B conversation', ?)
                """, Instant.now().toString(), Instant.now().toString(), OWNER_B);

        mockMvc.perform(sessionRequest(get("/api/accounts"), ownerASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Owner A Checking"))
                .andExpect(jsonPath("$[1]").doesNotExist());

        mockMvc.perform(sessionRequest(get("/api/imports/import-b"), ownerASession))
                .andExpect(status().isNotFound());
        mockMvc.perform(sessionRequest(get("/api/assistant/conversations/conversation-b"), ownerASession))
                .andExpect(status().isNotFound());
        mockMvc.perform(sessionRequest(patch("/api/transactions/transaction-b/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Groceries\",\"rememberForFuture\":false}"), ownerASession))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertEquals("Uncategorized", jdbcTemplate.queryForObject(
                "SELECT category FROM transactions WHERE id = 'transaction-b'", String.class));
    }

    @Test
    void requiresTheSessionBoundCsrfTokenForMutations() throws Exception {
        mockMvc.perform(sessionRequest(post("/api/assistant/conversations"), ownerASession, false))
                .andExpect(status().isForbidden());

        mockMvc.perform(sessionRequest(post("/api/assistant/conversations"), ownerASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());

        mockMvc.perform(sessionRequest(post("/api/auth/logout"), ownerASession, false))
                .andExpect(status().isForbidden());
        mockMvc.perform(sessionRequest(post("/api/auth/logout"), ownerASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedOut").value(true));
    }

    @Test
    void rejectsExpiredSessionsAndPurgesThemOutsideNormalRequests() throws Exception {
        Instant expired = Instant.now().minusSeconds(60);
        jdbcTemplate.update("""
                INSERT INTO finance_sessions (id, owner_user_id, csrf_token, created_at, last_seen_at, expires_at)
                VALUES ('expired-session', 'expired-owner', 'expired-csrf', ?, ?, ?)
                """, expired.toString(), expired.toString(), expired.toString());

        mockMvc.perform(get("/api/accounts").cookie(new Cookie("finance_session", "expired-session")))
                .andExpect(status().isUnauthorized());

        sessions.purgeExpired();
        Integer remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM finance_sessions WHERE id = 'expired-session'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, remaining);
    }

    @Test
    void allowsOnlyTheConfiguredCredentialedBrowserOrigin() throws Exception {
        mockMvc.perform(options("/api/accounts")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/accounts")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    private void createAccount(FinanceSession session, String name) throws Exception {
        mockMvc.perform(sessionRequest(post("/api/accounts/identify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institution\":\"Example Bank\",\"accountType\":\"CHECKING\",\"lastFour\":\"1234\",\"suggestedName\":\"" + name + "\",\"currency\":\"USD\"}"), session))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder sessionRequest(MockHttpServletRequestBuilder request, FinanceSession session) {
        return sessionRequest(request, session, true);
    }

    private MockHttpServletRequestBuilder sessionRequest(MockHttpServletRequestBuilder request, FinanceSession session, boolean csrf) {
        request.cookie(new Cookie("finance_session", session.id()));
        if (csrf) request.header(FinanceCsrfFilter.HEADER_NAME, session.csrfToken());
        return request;
    }
}
