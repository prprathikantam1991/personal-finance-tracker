package com.pradeep.finance.assistant;

import java.time.Instant;
import java.util.List;

public record ConversationResponse(String id, String title, Instant createdAt, Instant lastActiveAt,
                                   List<ConversationMessageResponse> messages) { }
