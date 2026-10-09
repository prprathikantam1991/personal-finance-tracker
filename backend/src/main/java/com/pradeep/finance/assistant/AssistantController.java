package com.pradeep.finance.assistant;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);
    private final LocalAssistantService localAssistantService;
    private final ObjectProvider<SpringAiAssistantService> springAiAssistantService;
    private final ConversationService conversationService;
    private final String runtime;
    public AssistantController(LocalAssistantService localAssistantService,
                               ObjectProvider<SpringAiAssistantService> springAiAssistantService,
                               ConversationService conversationService,
                               @Value("${finance.assistant.runtime:custom}") String runtime) {
        this.localAssistantService = localAssistantService;
        this.springAiAssistantService = springAiAssistantService;
        this.conversationService = conversationService;
        this.runtime = runtime;
    }
    @PostMapping("/chat") public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) { return selectedRuntime().chat(request.message(), request.conversation()); }
    @PostMapping("/agent-runs") public AgentRunResponse agentRun(@Valid @RequestBody AgentRunRequest request) {
        AgentRunResponse response = selectedRuntime().agentRun(request.message(), request.conversation());
        log.info("Assistant agent run completed: model={}, steps={}, tools={}, stopReason={}", response.model(), response.steps().size(), String.join(",", response.toolsUsed()), response.stopReason());
        return response;
    }
    @PostMapping("/conversations") public ConversationResponse createConversation() { return conversationService.create(); }
    @GetMapping("/conversations/{conversationId}") public ConversationResponse getConversation(@PathVariable String conversationId) { return conversationService.get(conversationId); }
    @DeleteMapping("/conversations/{conversationId}") public void deleteConversation(@PathVariable String conversationId) { conversationService.delete(conversationId); }
    @PostMapping("/conversations/{conversationId}/messages")
    public ConversationTurnResponse conversationTurn(@PathVariable String conversationId, @Valid @RequestBody ConversationTurnRequest request) {
        ConversationService.PromptContext prompt = conversationService.prepareTurn(conversationId, request.message());
        if (needsMultiStepRun(request.message())) {
            AgentRunResponse response = selectedRuntime().agentRun(request.message(), prompt.messages(), prompt.context());
            conversationService.completeTurn(conversationId, request.message(), response);
            return new ConversationTurnResponse(conversationId, response.answer(), response.toolsUsed(), response.model(), "AGENT_RUN", response.stopReason(), response.evidence(), response.steps());
        }
        AssistantChatResponse response = selectedRuntime().chat(request.message(), prompt.messages(), prompt.context());
        conversationService.completeTurn(conversationId, request.message(), response);
        return new ConversationTurnResponse(conversationId, response.answer(), response.toolsUsed(), response.model(), response.executionMode(), null, response.evidence(), List.of());
    }

    private AssistantRuntime selectedRuntime() {
        if (!"spring-ai-lm-studio".equalsIgnoreCase(runtime) && !"spring-ai-bedrock".equalsIgnoreCase(runtime)
                && !"spring-ai-bedrock-mantle".equalsIgnoreCase(runtime)) return localAssistantService;
        SpringAiAssistantService selected = springAiAssistantService.getIfAvailable();
        if (selected == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "The selected Spring AI runtime is not configured. Start the backend using the repository launcher and the matching runtime option.");
        }
        return selected;
    }

    /** Keeps orchestration an application decision so the normal UI never exposes a technical mode switch. */
    private boolean needsMultiStepRun(String question) {
        String normalized = question.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("compare") || normalized.contains(" then ")
                || (normalized.contains(" and ") && (normalized.contains("merchant") || normalized.contains("utilization")
                || normalized.contains("payment") || normalized.contains("recurring")));
    }
}
