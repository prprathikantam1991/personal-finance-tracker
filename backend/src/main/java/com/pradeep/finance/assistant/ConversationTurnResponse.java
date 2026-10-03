package com.pradeep.finance.assistant;

import java.util.List;

public record ConversationTurnResponse(String conversationId, String answer, List<String> toolsUsed, String model,
                                       String executionMode, String stopReason, List<String> evidence,
                                       List<AgentStep> steps) { }
