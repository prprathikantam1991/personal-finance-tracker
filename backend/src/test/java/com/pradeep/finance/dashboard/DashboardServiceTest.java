package com.pradeep.finance.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.pradeep.finance.transaction.MerchantNormalizer;

class DashboardServiceTest {

    @Test
    void netsCreditCardRefundsBeforeReturningMerchantSpending() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MerchantNormalizer normalizer = mock(MerchantNormalizer.class);
        when(normalizer.normalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(row("Walmart", new BigDecimal("34.06")));
            handler.processRow(row("Walmart", new BigDecimal("-15.00")));
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        DashboardService service = new DashboardService(jdbcTemplate, normalizer);

        var result = service.merchantSpending(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "Groceries");

        assertThat(result).singleElement().satisfies(merchant -> {
            assertThat(merchant.merchant()).isEqualTo("Walmart");
            assertThat(merchant.amount()).isEqualByComparingTo("19.06");
        });
    }

    private ResultSet row(String description, BigDecimal amount) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("description")).thenReturn(description);
        when(row.getString("category")).thenReturn("Groceries");
        when(row.getString("account_type")).thenReturn("CREDIT_CARD");
        when(row.getBigDecimal("amount")).thenReturn(amount);
        return row;
    }
}
