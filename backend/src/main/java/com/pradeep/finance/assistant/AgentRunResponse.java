package com.pradeep.finance.assistant;

import java.util.List;

public record AgentRunResponse(String answer, List<String> toolsUsed, List<AgentStep> steps,
                               String stopReason, String model, List<String> evidence) { }
