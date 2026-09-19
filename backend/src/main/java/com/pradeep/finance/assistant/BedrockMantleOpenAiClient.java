package com.pradeep.finance.assistant;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * OpenAI-compatible Bedrock Mantle adapter for models such as Google Gemma 4 31B.
 * A Bedrock API key is supplied only through BEDROCK_API_KEY; it is never persisted.
 */
@Component
@ConditionalOnProperty(name = "finance.assistant.provider", havingValue = "bedrock-mantle")
public class BedrockMantleOpenAiClient implements LocalModelClient {
    private final RestClient restClient;
    private final String model;

    public BedrockMantleOpenAiClient(@Value("${finance.assistant.bedrock.mantle.base-url}") String baseUrl,
                                     @Value("${finance.assistant.bedrock.mantle.api-key:}") String apiKey,
                                     @Value("${finance.assistant.bedrock.timeout-ms:60000}") long timeoutMs,
                                     @Value("${finance.assistant.model}") String model) {
        this.model = model;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(timeoutMs, 1000)));
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory);
        if (apiKey != null && !apiKey.isBlank()) builder.defaultHeader("Authorization", "Bearer " + apiKey);
        this.restClient = builder.build();
    }

    @Override
    public JsonNode complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools, int maxTokens) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", 0.1);
        request.put("max_tokens", maxTokens);
        if (tools != null) {
            request.put("tools", tools);
            request.put("tool_choice", "auto");
        }
        try {
            JsonNode response = restClient.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonNode.class);
            if (response == null || response.path("choices").isEmpty() || response.path("choices").path(0).path("message").isMissingNode()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Amazon Bedrock returned an unusable response.");
            }
            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Amazon Bedrock Mantle is not reachable. Check the region, Bedrock API key, and model access.", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Amazon Bedrock Mantle rejected this request. Check the Bedrock API key and selected model.", exception);
        }
    }
}
