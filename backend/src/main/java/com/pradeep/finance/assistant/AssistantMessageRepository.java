package com.pradeep.finance.assistant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantMessageRepository extends JpaRepository<AssistantMessage, String> {
    List<AssistantMessage> findByConversationIdOrderBySequenceNumberAsc(String conversationId);
    long countByConversationId(String conversationId);
    void deleteByConversationId(String conversationId);
}
