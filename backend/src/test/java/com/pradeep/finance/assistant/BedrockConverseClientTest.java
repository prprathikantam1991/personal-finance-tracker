package com.pradeep.finance.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

class BedrockConverseClientTest {

    @Test
    void normalizesBedrockToolUseWithoutBypassingTheExistingToolContract() {
        BedrockRuntimeClient runtime = org.mockito.Mockito.mock(BedrockRuntimeClient.class);
        ToolUseBlock toolUse = ToolUseBlock.builder().toolUseId("call-1").name("get_credit_utilization")
                .input(Document.fromMap(Map.of())).build();
        ConverseResponse response = ConverseResponse.builder().output(ConverseOutput.fromMessage(Message.builder()
                .role(ConversationRole.ASSISTANT).content(ContentBlock.builder().toolUse(toolUse).build()).build())).build();
        when(runtime.converse(any(ConverseRequest.class))).thenReturn(response);

        BedrockConverseClient client = new BedrockConverseClient(runtime, "test-model", new ObjectMapper());
        var normalized = client.complete(List.of(Map.of("role", "system", "content", "Use tools."),
                        Map.of("role", "user", "content", "What is my utilization?")),
                List.of(Map.of("type", "function", "function", Map.of("name", "get_credit_utilization",
                        "description", "Get utilization.", "parameters", Map.of("type", "object", "properties", Map.of())))), 160);

        assertThat(normalized.at("/choices/0/message/tool_calls/0/function/name").asText()).isEqualTo("get_credit_utilization");
        assertThat(normalized.at("/choices/0/message/tool_calls/0/function/arguments").asText()).isEqualTo("{}");
        ArgumentCaptor<ConverseRequest> request = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(runtime).converse(request.capture());
        assertThat(request.getValue().modelId()).isEqualTo("test-model");
        assertThat(request.getValue().toolConfig().tools()).hasSize(1);
        assertThat(request.getValue().system()).singleElement().satisfies(system -> assertThat(system.text()).isEqualTo("Use tools."));
    }
}
