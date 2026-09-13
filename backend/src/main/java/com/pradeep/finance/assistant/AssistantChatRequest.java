package com.pradeep.finance.assistant;

import java.util.List;
import jakarta.validation.constraints.NotBlank;

public record AssistantChatRequest(@NotBlank(message = "Ask a finance question.") String message, List<AssistantConversationMessage> conversation) {}
