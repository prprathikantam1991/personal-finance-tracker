package com.pradeep.finance.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SpringAiFinanceToolsTest {

    private final FinanceToolsService financeTools = mock(FinanceToolsService.class);
    private final ToolExecutionTrace toolTrace = new ToolExecutionTrace();
    private final SpringAiFinanceTools tools = new SpringAiFinanceTools(financeTools, toolTrace);

    @Test
    void delegatesAComparisonToTheExistingReadOnlyService() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        LocalDate compareFrom = LocalDate.of(2026, 8, 1);
        LocalDate compareTo = LocalDate.of(2026, 8, 31);
        FinanceToolsService.PeriodComparison expected = mock(FinanceToolsService.PeriodComparison.class);
        when(financeTools.comparePeriods(from, to, compareFrom, compareTo)).thenReturn(expected);

        assertThat(tools.comparePeriods(from, to, compareFrom, compareTo)).isSameAs(expected);

        verify(financeTools).comparePeriods(from, to, compareFrom, compareTo);
    }

    @Test
    void delegatesTheStrictPaydownCalculationWithoutImplementingItsOwnMath() {
        FinanceToolsService.CreditPaydownPlan expected = mock(FinanceToolsService.CreditPaydownPlan.class);
        when(financeTools.creditPaydownPlan(BigDecimal.TEN)).thenReturn(expected);

        assertThat(tools.creditPaydownPlan(BigDecimal.TEN)).isSameAs(expected);

        verify(financeTools).creditPaydownPlan(BigDecimal.TEN);
    }

    @Test
    void delegatesOptionalTransactionSearchFilters() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);

        tools.searchTransactions(null, from, to, "Groceries", "Patel Brothers");

        verify(financeTools).searchTransactions(null, from, to, "Groceries", "Patel Brothers");
    }
}
