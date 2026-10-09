package com.pradeep.finance.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Synthetic V4 agent evaluations. These deliberately simulate model replies so
 * the orchestration, allow-list, and budget behavior can be regression-tested
 * without sending a user's financial data to a model server.
 */
@ExtendWith(MockitoExtension.class)
class AgentRunEvaluationTest {

    @Mock private FinanceToolsService financeTools;
    @Mock private LocalModelClient localModelClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LocalAssistantService service;

    @BeforeEach
    void setUp() {
        service = new LocalAssistantService(financeTools, objectMapper, localModelClient, "synthetic-evaluation-model", 1200, 160);
    }

    @Test
    void completesASequentialTwoToolAnalysisWithAVisibleTrace() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"get_credit_utilization\",\"arguments\":\"{}\"}}]}"),
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-2\",\"type\":\"function\",\"function\":{\"name\":\"get_recurring_activity\",\"arguments\":\"{}\"}}]}"),
                response("{\"role\":\"assistant\",\"content\":\"Utilization is stable and no recurring risk was found.\"}"));
        when(financeTools.creditUtilization()).thenReturn(new FinanceToolsService.CreditUtilization(
                BigDecimal.valueOf(10_000), BigDecimal.valueOf(2_000), BigDecimal.valueOf(8_000),
                BigDecimal.valueOf(20), BigDecimal.valueOf(18), 1, 1, List.of()));
        when(financeTools.recurringActivity()).thenReturn(List.of());

        AgentRunResponse result = service.agentRun("Review my current credit position", List.of());

        assertThat(result.stopReason()).isEqualTo("COMPLETED");
        assertThat(result.answer()).contains("Utilization is stable");
        assertThat(result.toolsUsed()).containsExactly("get_credit_utilization", "get_recurring_activity");
        assertThat(result.steps()).extracting(AgentStep::tool).containsExactly("get_credit_utilization", "get_recurring_activity");
        verify(localModelClient, times(3)).complete(any(), any(), anyInt());
        verify(financeTools).creditUtilization();
        verify(financeTools).recurringActivity();
    }

    @Test
    void ignoresHarmlessPresentationArgumentsDuringASequentialReadOnlyPlan() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"compare_periods\",\"arguments\":\"{\\\"from\\\":\\\"2026-08-01\\\",\\\"to\\\":\\\"2026-08-31\\\",\\\"compareFrom\\\":\\\"2026-07-01\\\",\\\"compareTo\\\":\\\"2026-07-31\\\"}\"}}]}"),
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-2\",\"type\":\"function\",\"function\":{\"name\":\"get_merchant_spending\",\"arguments\":\"{\\\"from\\\":\\\"2026-08-01\\\",\\\"to\\\":\\\"2026-08-31\\\",\\\"limit\\\":5}\"}}]}"),
                response("{\"role\":\"assistant\",\"content\":\"August spending and its top merchants are ready.\"}"));
        when(financeTools.comparePeriods(any(), any(), any(), any())).thenReturn(null);
        when(financeTools.merchantSpending(any(), any())).thenReturn(List.of());

        AgentRunResponse result = service.agentRun("Compare July and August, then show August merchants.", List.of());

        assertThat(result.stopReason()).isEqualTo("COMPLETED");
        assertThat(result.toolsUsed()).containsExactly("compare_periods", "get_merchant_spending");
        verify(financeTools).comparePeriods(any(), any(), any(), any());
        verify(financeTools).merchantSpending(any(), any());
    }

    @Test
    void appliesTheResolvedPeriodAndSkipsARepeatedMerchantLookup() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"compare_periods\",\"arguments\":\"{\\\"from\\\":\\\"2026-08-01\\\",\\\"to\\\":\\\"2026-08-31\\\",\\\"compareFrom\\\":\\\"2026-07-01\\\",\\\"compareTo\\\":\\\"2026-07-31\\\"}\"}}]}"),
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-2\",\"type\":\"function\",\"function\":{\"name\":\"get_merchant_spending\",\"arguments\":\"{\\\"limit\\\":5}\"}}]}"),
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-3\",\"type\":\"function\",\"function\":{\"name\":\"get_merchant_spending\",\"arguments\":\"{}\"}}]}"),
                response("{\"role\":\"assistant\",\"content\":\"August spending and its top merchants are ready.\"}"));
        when(financeTools.comparePeriods(any(), any(), any(), any())).thenReturn(null);
        when(financeTools.merchantSpending(any(), any())).thenReturn(List.of());

        AgentRunResponse result = service.agentRun("Compare July and August 2026, then show top merchants for August.", List.of());

        assertThat(result.stopReason()).isEqualTo("COMPLETED");
        assertThat(result.toolsUsed()).containsExactly("compare_periods", "get_merchant_spending");
        assertThat(result.steps()).hasSize(2);
        verify(financeTools).merchantSpending(java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 8, 31));
    }

    @Test
    void normalizesTwoNamedMonthsBeforeExecutingAComparisonTool() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"compare_periods\",\"arguments\":\"{\\\"from\\\":\\\"2026-07-01\\\",\\\"to\\\":\\\"2026-08-31\\\",\\\"compareFrom\\\":\\\"2026-07-01\\\",\\\"compareTo\\\":\\\"2026-08-31\\\"}\"}}]}"),
                response("{\"role\":\"assistant\",\"content\":\"Comparison complete.\"}"));
        when(financeTools.comparePeriods(any(), any(), any(), any())).thenReturn(null);

        AgentRunResponse result = service.agentRun("Compare July and August 2026.", List.of());

        assertThat(result.stopReason()).isEqualTo("COMPLETED");
        verify(financeTools).comparePeriods(java.time.LocalDate.of(2026, 7, 1), java.time.LocalDate.of(2026, 7, 31), java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 8, 31));
    }

    @Test
    void usesTheFocusedCategoryComparisonToolForAWhyDidSpendingChangeQuestion() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"compare_category_spending\",\"arguments\":\"{\\\"category\\\":\\\"Groceries\\\"}\"}}]}"),
                response("{\"role\":\"assistant\",\"content\":\"Groceries increased because Patel Brothers was higher in August.\"}"));
        when(financeTools.compareCategory(any(), any(), any(), any(), any())).thenReturn(null);

        AgentRunResponse result = service.agentRun("Compare my grocery spending for July and August, then list the merchants responsible for the increase.", List.of());

        assertThat(result.stopReason()).isEqualTo("COMPLETED");
        assertThat(result.toolsUsed()).containsExactly("compare_category_spending");
        verify(financeTools).compareCategory(org.mockito.ArgumentMatchers.eq("Groceries"),
                org.mockito.ArgumentMatchers.eq(java.time.LocalDate.of(2026, 7, 1)),
                org.mockito.ArgumentMatchers.eq(java.time.LocalDate.of(2026, 7, 31)),
                org.mockito.ArgumentMatchers.eq(java.time.LocalDate.of(2026, 8, 1)),
                org.mockito.ArgumentMatchers.eq(java.time.LocalDate.of(2026, 8, 31)));
    }

    @Test
    void stopsBeforeExecutingWhenTheModelRequestsMultipleToolsInOneRound() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(response("{\"role\":\"assistant\",\"tool_calls\":["
                + "{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"get_credit_utilization\",\"arguments\":\"{}\"}},"
                + "{\"id\":\"call-2\",\"type\":\"function\",\"function\":{\"name\":\"get_recurring_activity\",\"arguments\":\"{}\"}}]}"));

        AgentRunResponse result = service.agentRun("Tell me everything", List.of());

        assertThat(result.stopReason()).isEqualTo("MULTIPLE_TOOLS_REQUESTED");
        assertThat(result.steps()).isEmpty();
        verify(financeTools, never()).creditUtilization();
        verify(financeTools, never()).recurringActivity();
    }

    @Test
    void usesGroundedFallbackWhenTheLocalModelDoesNotRequestAnyTool() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"content\":\"\"}"));
        when(financeTools.creditUtilization()).thenReturn(new FinanceToolsService.CreditUtilization(
                BigDecimal.valueOf(10_000), BigDecimal.valueOf(2_000), BigDecimal.valueOf(8_000),
                BigDecimal.valueOf(20), BigDecimal.valueOf(18), 1, 1, List.of()));

        AgentRunResponse result = service.agentRun("What is my overall credit utilization?", List.of());

        assertThat(result.stopReason()).isEqualTo("FALLBACK");
        assertThat(result.answer()).contains("20%");
        assertThat(result.steps()).singleElement().satisfies(step -> assertThat(step.outcome()).isEqualTo("Fallback"));
        verify(financeTools).creditUtilization();
    }

    @Test
    void calculatesMerchantFollowUpsFromDateFilteredTransactionsRatherThanTopMerchantAggregates() {
        when(financeTools.searchTransactions(eq(null), eq(java.time.LocalDate.of(2026, 8, 1)), eq(java.time.LocalDate.of(2026, 8, 31)), eq(null), eq("Patel Brothers")))
                .thenReturn(List.of(new com.pradeep.finance.transaction.TransactionResponse(
                        "transaction-1", "account-1", "Sample Card", com.pradeep.finance.account.AccountType.CREDIT_CARD,
                        java.time.LocalDate.of(2026, 8, 18), "PATEL BROTHERS", "Patel Brothers", BigDecimal.valueOf(68.05),
                        null, "Groceries", "HIGH", null, "CONFIRMED")));

        AssistantChatResponse result = service.chat("What about August?", List.of(
                new AssistantConversationMessage("user", "How much did I spend at Patel Brothers in July?")),
                new ConversationContext("2026-07-01", "2026-07-31", "Patel Brothers", null, null, null));

        assertThat(result.answer()).contains("$68.05").contains("Patel Brothers");
        assertThat(result.toolsUsed()).containsExactly("search_transactions");
        verify(financeTools).searchTransactions(eq(null), eq(java.time.LocalDate.of(2026, 8, 1)), eq(java.time.LocalDate.of(2026, 8, 31)), eq(null), eq("Patel Brothers"));
        verify(localModelClient, never()).complete(any(), any(), anyInt());
    }

    @Test
    void treatsNamedMerchantAndMonthShorthandAsAnExactLedgerLookup() {
        when(financeTools.searchTransactions(eq(null), eq(java.time.LocalDate.of(2026, 7, 1)), eq(java.time.LocalDate.of(2026, 7, 31)), eq(null), eq("Patel Brothers")))
                .thenReturn(List.of(new com.pradeep.finance.transaction.TransactionResponse(
                        "transaction-1", "account-1", "Sample Card", com.pradeep.finance.account.AccountType.CREDIT_CARD,
                        java.time.LocalDate.of(2026, 7, 18), "PATEL BROTHERS", "Patel Brothers", BigDecimal.valueOf(49.31),
                        null, "Groceries", "HIGH", null, "CONFIRMED")));

        AssistantChatResponse result = service.chat("Patel Brothers July", List.of());

        assertThat(result.answer()).contains("$49.31").contains("1 transaction");
        assertThat(result.toolsUsed()).containsExactly("search_transactions");
        verify(localModelClient, never()).complete(any(), any(), anyInt());
    }

    @Test
    void appliesTheResolvedPeriodBeforeExecutingAnAssistantModeCategoryLookup() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"get_category_spending\",\"arguments\":\"{}\"}}]}"),
                response("{\"role\":\"assistant\",\"content\":\"August category spending is ready.\"}"));
        when(financeTools.categorySpending(any(), any())).thenReturn(List.of());

        AssistantChatResponse result = service.chat("How much did I spend by category in August 2026?", List.of());

        assertThat(result.toolsUsed()).containsExactly("get_category_spending");
        verify(financeTools).categorySpending(java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 8, 31));
    }

    @Test
    void executesTheDeterministicCreditPaydownToolForAStrictTargetQuestion() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"get_credit_paydown_plan\",\"arguments\":\"{\\\"targetUtilizationPercent\\\":10}\"}}]}"),
                response("{\"role\":\"assistant\",\"content\":\"Pay $799.21 to get strictly below 10%.\"}"));
        when(financeTools.creditPaydownPlan(BigDecimal.TEN)).thenReturn(null);

        AgentRunResponse result = service.agentRun("How much should I pay to get my overall utilization below 10%?", List.of());

        assertThat(result.stopReason()).isEqualTo("COMPLETED");
        assertThat(result.toolsUsed()).containsExactly("get_credit_paydown_plan");
        verify(financeTools).creditPaydownPlan(BigDecimal.TEN);
    }

    @Test
    void rejectsAnOverlyLargeDateRangeBeforeExecutingTheRequestedTool() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"get_category_spending\",\"arguments\":\"{\\\"from\\\":\\\"2024-01-01\\\",\\\"to\\\":\\\"2026-09-01\\\"}\"}}]}"));

        AgentRunResponse result = service.agentRun("Show spending for August 2026", List.of());

        assertThat(result.stopReason()).isEqualTo("VALIDATION_STOP");
        assertThat(result.steps()).singleElement().satisfies(step -> {
            assertThat(step.tool()).isEqualTo("get_category_spending");
            assertThat(step.outcome()).isEqualTo("Rejected");
        });
        verify(financeTools, never()).categorySpending(any(), any());
    }

    @Test
    void rejectsAnUnknownToolBeforeItCanReachTheFinanceService() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"delete_all_transactions\",\"arguments\":\"{}\"}}]}"));

        AgentRunResponse result = service.agentRun("Delete all my transactions", List.of());

        assertThat(result.stopReason()).isEqualTo("VALIDATION_STOP");
        assertThat(result.steps()).singleElement().satisfies(step -> {
            assertThat(step.tool()).isEqualTo("delete_all_transactions");
            assertThat(step.outcome()).isEqualTo("Rejected");
        });
        verify(financeTools, never()).creditUtilization();
        verify(financeTools, never()).accountOverview();
    }

    @Test
    void rejectsANonNumericCreditPaydownTargetBeforeCalculation() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"get_credit_paydown_plan\",\"arguments\":\"{\\\"targetUtilizationPercent\\\":\\\"ten\\\"}\"}}]}"));

        AgentRunResponse result = service.agentRun("Bring my utilization below ten percent", List.of());

        assertThat(result.stopReason()).isEqualTo("VALIDATION_STOP");
        assertThat(result.steps()).singleElement().satisfies(step -> {
            assertThat(step.tool()).isEqualTo("get_credit_paydown_plan");
            assertThat(step.outcome()).isEqualTo("Rejected");
        });
        verify(financeTools, never()).creditPaydownPlan(any());
    }

    @Test
    void stopsAfterThreeValidatedToolRoundsAndRequestsAGroundedFinalAnswer() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"get_credit_utilization\",\"arguments\":\"{}\"}}]}"),
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-2\",\"type\":\"function\",\"function\":{\"name\":\"get_recurring_activity\",\"arguments\":\"{}\"}}]}"),
                response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-3\",\"type\":\"function\",\"function\":{\"name\":\"get_account_overview\",\"arguments\":\"{}\"}}]}"),
                response("{\"role\":\"assistant\",\"content\":\"Here is the grounded three-step summary.\"}"));
        when(financeTools.creditUtilization()).thenReturn(null);
        when(financeTools.recurringActivity()).thenReturn(List.of());
        when(financeTools.accountOverview()).thenReturn(List.of());

        AgentRunResponse result = service.agentRun("Review utilization, recurring activity, and my accounts.", List.of());

        assertThat(result.stopReason()).isEqualTo("TOOL_BUDGET_REACHED");
        assertThat(result.answer()).contains("grounded three-step summary");
        assertThat(result.steps()).extracting(AgentStep::tool)
                .containsExactly("get_credit_utilization", "get_recurring_activity", "get_account_overview");
        verify(localModelClient, times(4)).complete(any(), any(), anyInt());
    }

    @Test
    void asksForAPeriodBeforeSendingAnAmbiguousSpendingQuestionToTheModel() {
        AgentRunResponse result = service.agentRun("How much did I spend?", List.of());

        assertThat(result.stopReason()).isEqualTo("CLARIFICATION_REQUIRED");
        assertThat(result.answer()).contains("Which period should I use");
        assertThat(result.steps()).isEmpty();
        verify(localModelClient, never()).complete(any(), any(), anyInt());
        verify(financeTools, never()).monthlySummary(any(), any());
    }

    private JsonNode response(String message) throws Exception {
        return objectMapper.readTree("{\"choices\":[{\"message\":" + message + "}]}");
    }
}
