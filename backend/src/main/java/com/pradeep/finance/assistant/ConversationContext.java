package com.pradeep.finance.assistant;

/** Small, application-owned context. It is not model-written long-term memory. */
public record ConversationContext(String from, String to, String merchant, String category, String accountId,
                                  String unresolvedQuestion) {
    static ConversationContext empty() { return new ConversationContext(null, null, null, null, null, null); }
}
