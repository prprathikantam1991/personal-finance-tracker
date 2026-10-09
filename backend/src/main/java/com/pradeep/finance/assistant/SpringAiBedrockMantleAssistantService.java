package com.pradeep.finance.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Spring AI runtime for Bedrock Mantle's OpenAI-compatible endpoint. This keeps
 * the existing Gemma model and token authentication while replacing the custom
 * HTTP payload and tool-loop integration.
 */
@Service
@ConditionalOnProperty(name = "finance.assistant.runtime", havingValue = "spring-ai-bedrock-mantle")
public class SpringAiBedrockMantleAssistantService extends SpringAiAssistantService {
    public SpringAiBedrockMantleAssistantService(ChatClient.Builder chatClientBuilder, SpringAiFinanceTools financeTools,
                                                 ToolExecutionTrace toolTrace,
                                                 @Value("${finance.assistant.model}") String model) {
        super(chatClientBuilder, financeTools, toolTrace, model);
    }
}
