package com.pradeep.finance.assistant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assistant_messages")
class AssistantMessage {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;
    @Column(name = "conversation_id", nullable = false, updatable = false)
    private String conversationId;
    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;
    @Column(nullable = false, updatable = false)
    private String role;
    @Column(nullable = false, updatable = false)
    private String content;
    @Column(name = "created_at", nullable = false, updatable = false)
    private String createdAt;
    @Column(name = "execution_mode")
    private String executionMode;
    @Column(name = "tools_used")
    private String toolsUsed;
    private String evidence;
    @Column(name = "agent_steps")
    private String agentSteps;

    protected AssistantMessage() { }

    static AssistantMessage create(String conversationId, long sequenceNumber, String role, String content,
                                   String executionMode, String toolsUsed, String evidence, String agentSteps) {
        AssistantMessage message = new AssistantMessage();
        message.id = UUID.randomUUID().toString();
        message.conversationId = conversationId;
        message.sequenceNumber = sequenceNumber;
        message.role = role;
        message.content = content;
        message.executionMode = executionMode;
        message.toolsUsed = toolsUsed;
        message.evidence = evidence;
        message.agentSteps = agentSteps;
        message.createdAt = Instant.now().toString();
        return message;
    }

    String getRole() { return role; }
    String getContent() { return content; }
    String getExecutionMode() { return executionMode; }
    String getToolsUsed() { return toolsUsed; }
    String getEvidence() { return evidence; }
    String getAgentSteps() { return agentSteps; }
}
