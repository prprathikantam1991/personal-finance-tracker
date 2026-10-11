package com.pradeep.finance.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Spring AI runtime for Bedrock Mantle's OpenAI-compatible endpoint. This keeps
 * the existing Gemma model and token authentication while replacing the custom
 * HTTP payload and tool-loop integration.
 */
@Service
@ConditionalOnProperty(name = "finance.assistant.runtime", havingValue = "spring-ai-bedrock-mantle")
public class SpringAiBedrockMantleAssistantService extends SpringAiAssistantService {
    private final boolean tokenConfigured;

    public SpringAiBedrockMantleAssistantService(ChatClient.Builder chatClientBuilder, SpringAiFinanceTools financeTools,
                                                 ToolExecutionTrace toolTrace,
                                                 @Value("${finance.assistant.model}") String model,
                                                 @Value("${finance.assistant.bedrock.mantle.api-key:}") String apiKey) {
        super(chatClientBuilder, financeTools, toolTrace, model);
        this.tokenConfigured = apiKey != null && !apiKey.isBlank() && !"not-configured".equals(apiKey);
    }

    @Override
    public AssistantChatResponse chat(String question, List<AssistantConversationMessage> conversation,
                                      ConversationContext savedContext) {
        if (!tokenConfigured) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Amazon Bedrock is the default Assistant provider, but its API token is not configured. "
                            + "Set AWS_BEARER_TOKEN_BEDROCK or BEDROCK_API_KEY in your Windows user environment, open a new PowerShell window, and restart the backend.");
        }
        return super.chat(question, conversation, savedContext);
    }
}
