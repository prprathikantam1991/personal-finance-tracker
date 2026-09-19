package com.pradeep.finance.assistant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);
    private final LocalAssistantService localAssistantService;
    public AssistantController(LocalAssistantService localAssistantService) { this.localAssistantService = localAssistantService; }
    @PostMapping("/chat") public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) { return localAssistantService.chat(request.message(), request.conversation()); }
    @PostMapping("/agent-runs") public AgentRunResponse agentRun(@Valid @RequestBody AgentRunRequest request) {
        AgentRunResponse response = localAssistantService.agentRun(request.message(), request.conversation());
        log.info("Assistant agent run completed: model={}, steps={}, tools={}, stopReason={}", response.model(), response.steps().size(), String.join(",", response.toolsUsed()), response.stopReason());
        return response;
    }
}
