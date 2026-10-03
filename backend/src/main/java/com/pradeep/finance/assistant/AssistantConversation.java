package com.pradeep.finance.assistant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "assistant_conversations")
class AssistantConversation {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "last_active_at", nullable = false)
    private String lastActiveAt;

    private String title;

    protected AssistantConversation() { }

    static AssistantConversation create() { return new AssistantConversation(); }

    @PrePersist
    void initialize() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = now.toString();
        if (lastActiveAt == null) lastActiveAt = now.toString();
    }

    void touch() { lastActiveAt = Instant.now().toString(); }
    void titleIfAbsent(String value) { if (title == null && value != null && !value.isBlank()) title = value.length() > 72 ? value.substring(0, 72) : value; }

    String getId() { return id; }
    Instant getCreatedAt() { return Instant.parse(createdAt); }
    Instant getLastActiveAt() { return Instant.parse(lastActiveAt); }
    String getTitle() { return title; }
}
