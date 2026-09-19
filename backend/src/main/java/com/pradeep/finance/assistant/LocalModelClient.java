package com.pradeep.finance.assistant;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/** Narrow boundary around the local OpenAI-compatible model server. */
public interface LocalModelClient {
    JsonNode complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools, int maxTokens);
}
