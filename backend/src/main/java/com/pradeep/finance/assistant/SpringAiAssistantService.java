package com.pradeep.finance.assistant;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Experimental V6 runtime for an OpenAI-compatible local model. Spring AI owns
 * the model/tool protocol; application tools and data limits remain local.
 */
@Service
@ConditionalOnProperty(name = "finance.assistant.runtime", havingValue = "spring-ai-lm-studio")
@ConditionalOnBean(ChatClient.Builder.class)
public class SpringAiAssistantService implements AssistantRuntime {
    private final ChatClient chatClient;
    private final SpringAiFinanceTools financeTools;
    private final ToolExecutionTrace toolTrace;
    private final String model;

    public SpringAiAssistantService(ChatClient.Builder chatClientBuilder, SpringAiFinanceTools financeTools,
                                   ToolExecutionTrace toolTrace,
                                   @Value("${finance.assistant.model}") String model) {
        this.chatClient = chatClientBuilder.build();
        this.financeTools = financeTools;
        this.toolTrace = toolTrace;
        this.model = model;
    }

    @Override
    public AssistantChatResponse chat(String question, List<AssistantConversationMessage> conversation, ConversationContext savedContext) {
        try (ToolExecutionTrace.TraceSession trace = toolTrace.open()) {
            String answer = chatClient.prompt()
                    .system(systemPrompt(conversation, savedContext))
                    .user(question.trim())
                    .tools(financeTools)
                    .call()
                    .content();
            if (answer == null || answer.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The local model returned an empty response. Try again, or reload the model in LM Studio.");
            }
            List<String> tools = trace.toolsUsed();
            return new AssistantChatResponse(answer.trim(), tools, model,
                    tools.isEmpty() ? "SPRING_AI_MODEL_RESPONSE" : "SPRING_AI_TOOL_CALL", evidence(tools));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Spring AI local runtime could not complete the request. Confirm LM Studio is running with a tool-capable model, then try again.", exception);
        }
    }

    @Override
    public AgentRunResponse agentRun(String question, List<AssistantConversationMessage> conversation, ConversationContext savedContext) {
        AssistantChatResponse response = chat(question, conversation, savedContext);
        List<AgentStep> steps = new ArrayList<>();
        for (int index = 0; index < response.toolsUsed().size(); index++) {
            steps.add(new AgentStep(index + 1, response.toolsUsed().get(index), "Completed"));
        }
        return new AgentRunResponse(response.answer(), response.toolsUsed(), steps, "COMPLETED", response.model(), response.evidence());
    }

    private String systemPrompt(List<AssistantConversationMessage> conversation, ConversationContext savedContext) {
        StringBuilder prompt = new StringBuilder("You are the Personal Finance Tracker assistant. Today is ")
                .append(LocalDate.now())
                .append(". For finance facts, use the provided read-only tools before answering. ")
                .append("Only state values present in a tool result. Never invent transactions, dates, or calculations. ")
                .append("Tools cannot make payments or change data. Give a concise, complete answer to every requested part.");
        if (savedContext != null && !savedContext.equals(ConversationContext.empty())) {
            prompt.append(" Saved conversation context: ").append(savedContext);
        }
        if (conversation != null && !conversation.isEmpty()) {
            prompt.append(" Recent conversation:");
            conversation.stream().filter(message -> message.text() != null && !message.text().isBlank()).limit(12)
                    .forEach(message -> prompt.append("\n").append(message.role()).append(": ").append(message.text()));
        }
        return prompt.toString();
    }

    private List<String> evidence(List<String> tools) {
        if (tools.isEmpty()) return List.of("No finance-data tool was used for this response.");
        return List.of("Verified local ledger data", "Completed lookups: " + String.join(", ", tools));
    }
}
