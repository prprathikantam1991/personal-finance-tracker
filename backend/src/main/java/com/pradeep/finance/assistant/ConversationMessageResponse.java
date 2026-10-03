package com.pradeep.finance.assistant;

import java.util.List;

public record ConversationMessageResponse(String role, String text, String executionMode, List<String> toolsUsed,
                                          List<String> evidence, List<AgentStep> steps) { }
