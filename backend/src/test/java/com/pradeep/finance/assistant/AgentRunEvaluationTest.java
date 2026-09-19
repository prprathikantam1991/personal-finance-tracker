package com.pradeep.finance.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
        service = new LocalAssistantService(financeTools, objectMapper, localModelClient, "synthetic-evaluation-model");
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
    void rejectsAnOverlyLargeDateRangeBeforeExecutingTheRequestedTool() throws Exception {
        when(localModelClient.complete(any(), any(), anyInt())).thenReturn(response("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"get_category_spending\",\"arguments\":\"{\\\"from\\\":\\\"2024-01-01\\\",\\\"to\\\":\\\"2026-09-01\\\"}\"}}]}"));

        AgentRunResponse result = service.agentRun("Show spending", List.of());

        assertThat(result.stopReason()).isEqualTo("VALIDATION_STOP");
        assertThat(result.steps()).singleElement().satisfies(step -> {
            assertThat(step.tool()).isEqualTo("get_category_spending");
            assertThat(step.outcome()).isEqualTo("Rejected");
        });
        verify(financeTools, never()).categorySpending(any(), any());
    }

    private JsonNode response(String message) throws Exception {
        return objectMapper.readTree("{\"choices\":[{\"message\":" + message + "}]}");
    }
}
