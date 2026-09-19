package com.pradeep.finance.assistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;

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
}
