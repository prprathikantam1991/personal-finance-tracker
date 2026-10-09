package com.pradeep.finance.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Spring AI runtime for a local OpenAI-compatible LM Studio server. */
@Service
@ConditionalOnProperty(name = "finance.assistant.runtime", havingValue = "spring-ai-lm-studio")
public class SpringAiLmStudioAssistantService extends SpringAiAssistantService {
    public SpringAiLmStudioAssistantService(ChatClient.Builder chatClientBuilder, SpringAiFinanceTools financeTools,
                                            ToolExecutionTrace toolTrace,
                                            @Value("${finance.assistant.model}") String model) {
        super(chatClientBuilder, financeTools, toolTrace, model);
    }
}
