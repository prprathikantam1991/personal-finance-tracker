package com.pradeep.finance.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Spring AI runtime backed by Amazon Bedrock Converse and the local AWS credential chain. */
@Service
@ConditionalOnProperty(name = "finance.assistant.runtime", havingValue = "spring-ai-bedrock")
public class SpringAiBedrockAssistantService extends SpringAiAssistantService {
    public SpringAiBedrockAssistantService(ChatClient.Builder chatClientBuilder, SpringAiFinanceTools financeTools,
                                           ToolExecutionTrace toolTrace,
                                           @Value("${finance.assistant.model}") String model) {
        super(chatClientBuilder, financeTools, toolTrace, model);
    }
}
