package com.pradeep.finance.assistant;

import java.util.List;

/** executionMode makes it clear whether the model selected a tool or a bounded reliability fallback answered. */
public record AssistantChatResponse(String answer, List<String> toolsUsed, String model, String executionMode, List<String> evidence) {
    public AssistantChatResponse(String answer, List<String> toolsUsed, String model, String executionMode) {
        this(answer, toolsUsed, model, executionMode, List.of());
    }
}
