package com.pradeep.finance.assistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
