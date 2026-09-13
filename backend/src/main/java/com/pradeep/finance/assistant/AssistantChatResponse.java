package com.pradeep.finance.assistant;

import java.util.List;

public record AssistantChatResponse(String answer, List<String> toolsUsed, String model) {}
