package com.pradeep.finance.assistant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final LocalAssistantService localAssistantService;
    public AssistantController(LocalAssistantService localAssistantService) { this.localAssistantService = localAssistantService; }
    @PostMapping("/chat") public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) { return localAssistantService.chat(request.message(), request.conversation()); }
    @PostMapping("/agent-runs") public AgentRunResponse agentRun(@Valid @RequestBody AgentRunRequest request) { return localAssistantService.agentRun(request.message(), request.conversation()); }
}
