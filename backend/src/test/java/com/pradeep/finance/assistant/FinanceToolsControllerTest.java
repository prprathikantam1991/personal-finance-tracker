package com.pradeep.finance.assistant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pradeep.finance.account.AccountType;

@WebMvcTest(FinanceToolsController.class)
class FinanceToolsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinanceToolsService financeToolsService;

    @Test
    void exposesCompactAccountContext() throws Exception {
        when(financeToolsService.accountOverview()).thenReturn(List.of(account()));

        mockMvc.perform(get("/api/finance-tools/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sample Card"))
                .andExpect(jsonPath("$[0].creditLimit").value(10000));
    }

    @Test
    void exposesBoundedAccountStatementHistory() throws Exception {
        var snapshot = new FinanceToolsService.AccountSnapshotContext(LocalDate.of(2026, 8, 31), new BigDecimal("100.00"), new BigDecimal("10000.00"), new BigDecimal("9900.00"), null, null);
        when(financeToolsService.accountHistory(anyString())).thenReturn(new FinanceToolsService.AccountHistoryContext(account(), List.of(snapshot)));

        mockMvc.perform(get("/api/finance-tools/accounts/account-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value("account-1"))
                .andExpect(jsonPath("$.statements[0].statementAsOf").value("2026-08-31"));
    }

    private FinanceToolsService.AccountContext account() {
        return new FinanceToolsService.AccountContext("account-1", "Sample Card", "Sample Bank", AccountType.CREDIT_CARD, "1234",
                new BigDecimal("100.00"), new BigDecimal("10000.00"), new BigDecimal("9900.00"), new BigDecimal("1.0"),
                null, null, null, new BigDecimal("25.00"), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 8, 31));
    }
}
