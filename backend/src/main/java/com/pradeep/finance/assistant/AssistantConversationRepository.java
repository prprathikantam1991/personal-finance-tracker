package com.pradeep.finance.assistant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, String> {
    Optional<AssistantConversation> findByIdAndOwnerUserId(String id, String ownerUserId);
}
