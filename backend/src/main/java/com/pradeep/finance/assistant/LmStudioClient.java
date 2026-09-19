package com.pradeep.finance.assistant;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

/** Production adapter for LM Studio's local OpenAI-compatible chat endpoint. */
@Component
public class LmStudioClient implements LocalModelClient {
    private final RestClient restClient;
    private final String model;

    public LmStudioClient(@Value("${finance.assistant.lm-studio.base-url}") String baseUrl,
                          @Value("${finance.assistant.lm-studio.model}") String model,
                          @Value("${finance.assistant.lm-studio.api-key:}") String apiKey,
                          @Value("${finance.assistant.lm-studio.timeout-ms:60000}") long timeoutMs) {
        this.model = model;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(timeoutMs, 1000)));
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory);
        if (apiKey != null && !apiKey.isBlank()) builder.defaultHeader("Authorization", "Bearer " + apiKey);
        this.restClient = builder.build();
    }

    @Override
    public JsonNode complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", 0.1);
        request.put("max_tokens", 700);
        if (tools != null) {
            request.put("tools", tools);
            request.put("tool_choice", "auto");
        }
        try {
            JsonNode response = restClient.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonNode.class);
            if (response == null || response.path("choices").isEmpty() || response.path("choices").path(0).path("message").isMissingNode()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The local model returned an unusable response. Try again, or reload the model in LM Studio.");
            }
            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The local model took longer than expected. It may still be loading; wait a moment and try again.", exception);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LM Studio is not ready. Start its local server and load a model, then try again.", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LM Studio is not ready. Start its local server and load a model, then try again.", exception);
        }
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException || current instanceof java.net.http.HttpTimeoutException) return true;
            current = current.getCause();
        }
        return false;
    }
}
