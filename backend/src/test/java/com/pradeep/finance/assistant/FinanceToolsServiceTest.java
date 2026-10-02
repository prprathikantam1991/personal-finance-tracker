package com.pradeep.finance.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pradeep.finance.account.AccountOverviewResponse;
import com.pradeep.finance.account.AccountOverviewService;
import com.pradeep.finance.account.AccountType;
import com.pradeep.finance.dashboard.DashboardService;
import com.pradeep.finance.recurring.RecurringTransactionService;
import com.pradeep.finance.transaction.TransactionService;

class FinanceToolsServiceTest {

    private final AccountOverviewService accounts = mock(AccountOverviewService.class);
    private final FinanceToolsService service = new FinanceToolsService(mock(DashboardService.class), accounts,
            mock(RecurringTransactionService.class), mock(TransactionService.class));

    @Test
    void calculatesTheExtraCentNeededForAStrictlyBelowTarget() {
        AccountOverviewResponse discover = card("Discover", "3558", "6499.20", "12500", "24.99", null, null);
        AccountOverviewResponse wells = card("Wells Fargo", "0286", "100.00", "10500", "22.74", null, null);
        AccountOverviewResponse amex = card("American Express", "1004", "0.00", "15000", "28.49", null, null);
        AccountOverviewResponse bofa = card("Bank of America", "7098", "0.00", "20000", "19.99", null, null);
        when(accounts.list()).thenReturn(List.of(discover, wells, amex, bofa));

        FinanceToolsService.CreditPaydownPlan plan = service.creditPaydownPlan(BigDecimal.TEN);

        assertThat(plan.planningAvailable()).isTrue();
        assertThat(plan.totalLimit()).isEqualByComparingTo("58000.00");
        assertThat(plan.currentUtilized()).isEqualByComparingTo("6599.20");
        assertThat(plan.maximumUtilizedBelowTarget()).isEqualByComparingTo("5799.99");
        assertThat(plan.requiredPayment()).isEqualByComparingTo("799.21");
        assertThat(plan.projectedUtilizationPercent()).isLessThan(BigDecimal.TEN);
        assertThat(plan.cardPriorities().getFirst().accountName()).isEqualTo("Discover");
        assertThat(plan.cardPriorities().getFirst().suggestedPayment()).isEqualByComparingTo("799.21");
    }

    @Test
    void reportsUnavailableWhenNoCardLimitsAreSaved() {
        AccountOverviewResponse unconfigured = card("Unconfigured Card", "9999", "50.00", null, null, null, null);
        when(accounts.list()).thenReturn(List.of(unconfigured));

        FinanceToolsService.CreditPaydownPlan plan = service.creditPaydownPlan(BigDecimal.TEN);

        assertThat(plan.planningAvailable()).isFalse();
        assertThat(plan.requiredPayment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(plan.cardPriorities()).isEmpty();
    }

    @Test
    void returnsNoPaymentWhenAlreadyBelowTheStrictTarget() {
        AccountOverviewResponse discover = card("Discover", "3558", "999.99", "10000", "24.99", "0", "2027-07-01");
        when(accounts.list()).thenReturn(List.of(discover));

        FinanceToolsService.CreditPaydownPlan plan = service.creditPaydownPlan(BigDecimal.TEN);

        assertThat(plan.requiredPayment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(plan.status()).contains("already strictly below");
        assertThat(plan.cardPriorities().getFirst().promotionalAprExpiresOn()).isEqualTo("2027-07-01");
    }

    private AccountOverviewResponse card(String name, String lastFour, String balance, String limit, String apr,
                                         String promotionalApr, String promotionalAprExpiresOn) {
        AccountOverviewResponse card = mock(AccountOverviewResponse.class);
        when(card.accountType()).thenReturn(AccountType.CREDIT_CARD);
        when(card.name()).thenReturn(name);
        when(card.lastFour()).thenReturn(lastFour);
        when(card.statementBalance()).thenReturn(number(balance));
        when(card.creditLimit()).thenReturn(number(limit));
        when(card.currentApr()).thenReturn(number(apr));
        when(card.promotionalApr()).thenReturn(number(promotionalApr));
        when(card.promotionalAprExpiresOn()).thenReturn(promotionalAprExpiresOn);
        return card;
    }

    private BigDecimal number(String value) { return value == null ? null : new BigDecimal(value); }
}
