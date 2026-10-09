package com.pradeep.finance.assistant;

import java.util.List;

/** A migration seam: the HTTP API stays stable while model plumbing can change safely. */
public interface AssistantRuntime {
    default AssistantChatResponse chat(String question, List<AssistantConversationMessage> conversation) {
        return chat(question, conversation, ConversationContext.empty());
    }

    AssistantChatResponse chat(String question, List<AssistantConversationMessage> conversation, ConversationContext savedContext);

    default AgentRunResponse agentRun(String question, List<AssistantConversationMessage> conversation) {
        return agentRun(question, conversation, ConversationContext.empty());
    }

    AgentRunResponse agentRun(String question, List<AssistantConversationMessage> conversation, ConversationContext savedContext);
}
