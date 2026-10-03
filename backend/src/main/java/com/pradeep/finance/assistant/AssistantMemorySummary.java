package com.pradeep.finance.assistant;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assistant_memory_summaries")
class AssistantMemorySummary {

    @Id
    @Column(name = "conversation_id", nullable = false, updatable = false)
    private String conversationId;
    @Column(name = "structured_context", nullable = false)
    private String structuredContext;
    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    protected AssistantMemorySummary() { }

    static AssistantMemorySummary create(String conversationId, String structuredContext) {
        AssistantMemorySummary summary = new AssistantMemorySummary();
        summary.conversationId = conversationId;
        summary.structuredContext = structuredContext;
        summary.updatedAt = Instant.now().toString();
        return summary;
    }

    void update(String value) { structuredContext = value; updatedAt = Instant.now().toString(); }
    String getStructuredContext() { return structuredContext; }
}
