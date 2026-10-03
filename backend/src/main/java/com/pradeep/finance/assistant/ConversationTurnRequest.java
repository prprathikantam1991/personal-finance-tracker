package com.pradeep.finance.assistant;

import jakarta.validation.constraints.NotBlank;

public record ConversationTurnRequest(@NotBlank(message = "Ask a finance question.") String message, boolean agentMode) { }
