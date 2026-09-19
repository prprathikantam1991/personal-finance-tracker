package com.pradeep.finance.assistant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkNumber;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Bedrock Runtime / Converse adapter. It uses the standard AWS credential chain
 * (or an explicitly selected local profile), so no AWS secret is stored in this application.
 *
 * <p>It deliberately normalizes Bedrock replies into the existing OpenAI-shaped model contract.
 * This keeps the finance-tool allow-list and agent safety checks provider-independent.</p>
 */
@Component
@ConditionalOnProperty(name = "finance.assistant.provider", havingValue = "bedrock")
public class BedrockConverseClient implements LocalModelClient {
    private final BedrockRuntimeClient client;
    private final String model;
    private final ObjectMapper objectMapper;

    public BedrockConverseClient(@Value("${finance.assistant.bedrock.region}") String region,
                                 @Value("${finance.assistant.bedrock.profile:}") String profile,
                                 @Value("${finance.assistant.bedrock.timeout-ms:60000}") long timeoutMs,
                                 @Value("${finance.assistant.model}") String model,
                                 ObjectMapper objectMapper) {
        this(BedrockRuntimeClient.builder()
                        .region(Region.of(region))
                        .credentialsProvider(credentials(profile))
                        .overrideConfiguration(configuration -> configuration.apiCallTimeout(Duration.ofMillis(Math.max(timeoutMs, 1000))))
                        .build(), model, objectMapper);
    }

    BedrockConverseClient(BedrockRuntimeClient client, String model, ObjectMapper objectMapper) {
        this.client = client;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools, int maxTokens) {
        try {
            ConverseRequest.Builder request = ConverseRequest.builder()
                    .modelId(model)
                    .messages(conversation(messages))
                    .system(system(messages))
                    .inferenceConfig(InferenceConfiguration.builder().maxTokens(maxTokens).temperature(0.1f).build());
            if (tools != null) request.toolConfig(ToolConfiguration.builder().tools(toolDefinitions(tools)).build());
            return normalize(client.converse(request.build()));
        } catch (AwsServiceException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Amazon Bedrock could not complete this request. Check model access, region, and the selected model.", exception);
        } catch (SdkClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Amazon Bedrock credentials are not available to this application. Configure the selected local AWS profile and try again.", exception);
        }
    }

    private static AwsCredentialsProvider credentials(String profile) {
        return profile == null || profile.isBlank()
                ? DefaultCredentialsProvider.create()
                : ProfileCredentialsProvider.builder().profileName(profile).build();
    }

    private List<SystemContentBlock> system(List<Map<String, Object>> messages) {
        return messages.stream().filter(message -> "system".equals(message.get("role")))
                .map(message -> SystemContentBlock.builder().text(String.valueOf(message.getOrDefault("content", ""))).build()).toList();
    }

    private List<Message> conversation(List<Map<String, Object>> messages) {
        List<Message> result = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            String role = String.valueOf(message.get("role"));
            if ("system".equals(role)) continue;
            if ("tool".equals(role)) {
                result.add(Message.builder().role(ConversationRole.USER).content(toolResult(message)).build());
            } else if ("assistant".equals(role) && message.containsKey("tool_calls")) {
                result.add(Message.builder().role(ConversationRole.ASSISTANT).content(toolUses(message)).build());
            } else {
                result.add(Message.builder().role("assistant".equals(role) ? ConversationRole.ASSISTANT : ConversationRole.USER)
                        .content(ContentBlock.builder().text(String.valueOf(message.getOrDefault("content", ""))).build()).build());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<ContentBlock> toolUses(Map<String, Object> message) {
        List<ContentBlock> result = new ArrayList<>();
        for (Object item : (List<Object>) message.get("tool_calls")) {
            Map<String, Object> call = (Map<String, Object>) item;
            Map<String, Object> function = (Map<String, Object>) call.get("function");
            result.add(ContentBlock.builder().toolUse(ToolUseBlock.builder()
                    .toolUseId(String.valueOf(call.get("id")))
                    .name(String.valueOf(function.get("name")))
                    .input(document(parse(String.valueOf(function.getOrDefault("arguments", "{}")))))
                    .build()).build());
        }
        return result;
    }

    private List<ContentBlock> toolResult(Map<String, Object> message) {
        ToolResultBlock result = ToolResultBlock.builder()
                .toolUseId(String.valueOf(message.get("tool_call_id")))
                .content(ToolResultContentBlock.builder().text(String.valueOf(message.getOrDefault("content", "{}"))).build())
                .build();
        return List.of(ContentBlock.builder().toolResult(result).build());
    }

    @SuppressWarnings("unchecked")
    private List<Tool> toolDefinitions(List<Map<String, Object>> definitions) {
        List<Tool> result = new ArrayList<>();
        for (Map<String, Object> definition : definitions) {
            Map<String, Object> function = (Map<String, Object>) definition.get("function");
            result.add(Tool.builder().toolSpec(ToolSpecification.builder()
                    .name(String.valueOf(function.get("name")))
                    .description(String.valueOf(function.get("description")))
                    .inputSchema(ToolInputSchema.builder().json(document(objectMapper.valueToTree(function.get("parameters")))).build())
                    .build()).build());
        }
        return result;
    }

    private JsonNode normalize(ConverseResponse response) {
        var message = objectMapper.createObjectNode();
        var calls = objectMapper.createArrayNode();
        StringBuilder text = new StringBuilder();
        response.output().message().content().forEach(block -> {
            if (block.text() != null) text.append(block.text());
            if (block.toolUse() != null) {
                ToolUseBlock use = block.toolUse();
                var call = calls.addObject();
                call.put("id", use.toolUseId());
                call.put("type", "function");
                var function = call.putObject("function");
                function.put("name", use.name());
                function.put("arguments", json(javaValue(use.input())));
            }
        });
        message.put("content", text.toString());
        if (!calls.isEmpty()) message.set("tool_calls", calls);
        var root = objectMapper.createObjectNode();
        root.putArray("choices").addObject().set("message", message);
        return root;
    }

    private JsonNode parse(String value) {
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Model tool arguments were not JSON.", exception); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not normalize Bedrock response.", exception); }
    }

    private Document document(JsonNode value) {
        if (value.isObject()) {
            Map<String, Document> fields = new LinkedHashMap<>();
            value.fields().forEachRemaining(field -> fields.put(field.getKey(), document(field.getValue())));
            return Document.fromMap(fields);
        }
        if (value.isArray()) {
            List<Document> values = new ArrayList<>();
            value.forEach(item -> values.add(document(item)));
            return Document.fromList(values);
        }
        if (value.isNumber()) return Document.fromNumber(SdkNumber.fromBigDecimal(value.decimalValue()));
        if (value.isBoolean()) return Document.fromBoolean(value.booleanValue());
        // Tool schemas and validated tool arguments do not use null. Representing an
        // unexpected null as an empty string keeps the SDK document valid and safe.
        if (value.isNull()) return Document.fromString("");
        return Document.fromString(value.asText());
    }

    private Object javaValue(Document value) {
        if (value.isMap()) return value.asMap().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> javaValue(entry.getValue()), (left, right) -> right, LinkedHashMap::new));
        if (value.isList()) return value.asList().stream().map(this::javaValue).toList();
        if (value.isNumber()) return value.asNumber().bigDecimalValue();
        if (value.isBoolean()) return value.asBoolean();
        if (value.isNull()) return null;
        return value.asString();
    }
}
