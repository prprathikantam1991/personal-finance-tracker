package com.pradeep.finance.assistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(AssistantController.class)
class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocalAssistantService localAssistantService;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    void returnsGatewayTimeoutWhenTheLocalModelIsSlow() throws Exception {
        when(localAssistantService.chat(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The local model took longer than expected."));

        mockMvc.perform(post("/api/assistant/chat").contentType("application/json")
                        .content("{\"message\":\"How much did I spend last month?\",\"conversation\":[]}"))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void returnsServiceUnavailableWhenLmStudioCannotBeReached() throws Exception {
        when(localAssistantService.chat(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LM Studio is not ready."));

        mockMvc.perform(post("/api/assistant/chat").contentType("application/json")
                        .content("{\"message\":\"What is my credit utilization?\",\"conversation\":[]}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void exposesTheAgentTraceForACompletedAgentRun() throws Exception {
        when(localAssistantService.agentRun(any(), any())).thenReturn(new AgentRunResponse(
                "Your utilization is 20%.", List.of("get_credit_utilization"),
                List.of(new AgentStep(1, "get_credit_utilization", "Completed")),
                "COMPLETED", "synthetic-evaluation-model", List.of("Confirmed saved finance data")));

        mockMvc.perform(post("/api/assistant/agent-runs").contentType("application/json")
                        .content("{\"message\":\"What is my credit utilization?\",\"conversation\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopReason").value("COMPLETED"))
                .andExpect(jsonPath("$.steps[0].tool").value("get_credit_utilization"));
    }

    @Test
    void returnsServiceUnavailableWhenAgentRunCannotReachLmStudio() throws Exception {
        when(localAssistantService.agentRun(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LM Studio is not ready."));

        mockMvc.perform(post("/api/assistant/agent-runs").contentType("application/json")
                        .content("{\"message\":\"What is my credit utilization?\",\"conversation\":[]}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void returnsASafeDetailForAssistantFailures() throws Exception {
        when(localAssistantService.chat(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The selected AI provider rejected its access token."));

        mockMvc.perform(post("/api/assistant/chat").contentType("application/json")
                        .content("{\"message\":\"Are you available?\",\"conversation\":[]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The selected AI provider rejected its access token."));
    }

    @Test
    void createsAndRestoresABackendOwnedConversation() throws Exception {
        ConversationResponse response = new ConversationResponse("conversation-1", "First question", Instant.parse("2026-10-08T12:00:00Z"),
                Instant.parse("2026-10-08T12:00:00Z"), List.of(new ConversationMessageResponse("user", "Hello", null, List.of(), List.of(), List.of())));
        when(conversationService.create()).thenReturn(response);
        when(conversationService.get("conversation-1")).thenReturn(response);

        mockMvc.perform(post("/api/assistant/conversations").contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("conversation-1"));
        mockMvc.perform(get("/api/assistant/conversations/conversation-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].text").value("Hello"));
    }

    @Test
    void savesAConversationTurnUsingServerLoadedPromptContext() throws Exception {
        when(conversationService.prepareTurn(any(), any())).thenReturn(new ConversationService.PromptContext(
                List.of(new AssistantConversationMessage("user", "Earlier question")),
                new ConversationContext("2026-08-01", "2026-08-31", "Patel Brothers", "Groceries", null, null)));
        when(localAssistantService.chat(any(), any(), any())).thenReturn(new AssistantChatResponse(
                "Grounded answer", List.of("get_merchant_spending"), "test-model", "TOOL_CALL", List.of("Confirmed saved finance data")));

        mockMvc.perform(post("/api/assistant/conversations/conversation-1/messages").contentType("application/json")
                        .content("{\"message\":\"What about August?\",\"agentMode\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conversation-1"))
                .andExpect(jsonPath("$.toolsUsed[0]").value("get_merchant_spending"));
    }

    @Test
    void automaticallyUsesTheAgentPathForAMultiStepQuestion() throws Exception {
        when(conversationService.prepareTurn(any(), any())).thenReturn(new ConversationService.PromptContext(List.of(), ConversationContext.empty()));
        when(localAssistantService.agentRun(any(), any(), any())).thenReturn(new AgentRunResponse(
                "July and August comparison.", List.of("compare_periods", "get_merchant_spending"),
                List.of(new AgentStep(1, "compare_periods", "Completed"), new AgentStep(2, "get_merchant_spending", "Completed")),
                "COMPLETED", "test-model", List.of("Verified local ledger data")));

        mockMvc.perform(post("/api/assistant/conversations/conversation-1/messages").contentType("application/json")
                        .content("{\"message\":\"Compare July and August, then show top merchants for August.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("AGENT_RUN"))
                .andExpect(jsonPath("$.steps").isArray());

        verify(localAssistantService).agentRun(any(), any(), any());
    }
}
