package com.pradeep.finance.assistant;

import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantConversationRepository extends JpaRepository<AssistantConversation, String> { }
