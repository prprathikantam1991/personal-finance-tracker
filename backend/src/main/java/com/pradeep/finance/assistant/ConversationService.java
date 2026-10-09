package com.pradeep.finance.assistant;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Owns local conversation lifecycle and bounded prompt context; the model never owns either. */
@Service
public class ConversationService {
    private static final int PROMPT_MESSAGE_LIMIT = 12;
    private static final Pattern MONTH_NAME = Pattern.compile("(?i)\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\b");
    private static final Pattern MERCHANT = Pattern.compile("(?i)\\bat\\s+(.+?)(?=\\s+(?:for|in|during)\\b|[?!.]?$)");
    private final AssistantConversationRepository conversations;
    private final AssistantMessageRepository messages;
    private final AssistantMemorySummaryRepository summaries;
    private final ObjectMapper objectMapper;

    public ConversationService(AssistantConversationRepository conversations, AssistantMessageRepository messages,
                               AssistantMemorySummaryRepository summaries, ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.messages = messages;
        this.summaries = summaries;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConversationResponse create() {
        AssistantConversation conversation = conversations.save(AssistantConversation.create());
        summaries.save(AssistantMemorySummary.create(conversation.getId(), json(ConversationContext.empty())));
        return response(conversation, List.of());
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(String conversationId) {
        AssistantConversation conversation = requireConversation(conversationId);
        return response(conversation, messages.findByConversationIdOrderBySequenceNumberAsc(conversationId));
    }

    @Transactional
    public void delete(String conversationId) {
        AssistantConversation conversation = requireConversation(conversationId);
        messages.deleteByConversationId(conversationId);
        summaries.deleteById(conversationId);
        conversations.delete(conversation);
    }

    @Transactional
    public PromptContext prepareTurn(String conversationId, String question) {
        AssistantConversation conversation = requireConversation(conversationId);
        conversation.titleIfAbsent(question);
        conversation.touch();
        List<AssistantConversationMessage> contextMessages = recentMessages(conversationId);
        ConversationContext context = loadContext(conversationId);
        int sequence = nextSequence(conversationId);
        messages.save(AssistantMessage.create(conversationId, sequence, "user", question, null, null, null, null));
        return new PromptContext(contextMessages, context);
    }

    @Transactional
    public void completeTurn(String conversationId, String question, AssistantChatResponse response) {
        int sequence = nextSequence(conversationId);
        messages.save(AssistantMessage.create(conversationId, sequence, "assistant", response.answer(), response.executionMode(),
                json(response.toolsUsed()), json(response.evidence()), null));
        updateContext(conversationId, question);
    }

    @Transactional
    public void completeTurn(String conversationId, String question, AgentRunResponse response) {
        int sequence = nextSequence(conversationId);
        messages.save(AssistantMessage.create(conversationId, sequence, "assistant", response.answer(), "AGENT_RUN",
                json(response.toolsUsed()), json(response.evidence()), json(response.steps())));
        updateContext(conversationId, question);
    }

    private List<AssistantConversationMessage> recentMessages(String conversationId) {
        List<AssistantMessage> all = messages.findByConversationIdOrderBySequenceNumberAsc(conversationId);
        int fromIndex = Math.max(0, all.size() - PROMPT_MESSAGE_LIMIT);
        return all.subList(fromIndex, all.size()).stream()
                .map(message -> new AssistantConversationMessage(message.getRole(), message.getContent())).toList();
    }

    private int nextSequence(String conversationId) {
        return Math.toIntExact(messages.countByConversationId(conversationId) + 1);
    }

    private void updateContext(String conversationId, String question) {
        ConversationContext current = loadContext(conversationId);
        ConversationContext updated = new ConversationContext(
                monthFrom(question, true, current.from()), monthFrom(question, false, current.to()),
                firstMatch(MERCHANT, question, current.merchant()), category(question, current.category()), current.accountId(), null);
        AssistantMemorySummary summary = summaries.findById(conversationId)
                .orElseGet(() -> AssistantMemorySummary.create(conversationId, json(ConversationContext.empty())));
        summary.update(json(updated));
        summaries.save(summary);
    }

    private String monthFrom(String question, boolean start, String fallback) {
        Matcher matcher = MONTH_NAME.matcher(question);
        YearMonth month = null;
        while (matcher.find()) month = YearMonth.of(LocalDate.now().getYear(), monthNumber(matcher.group(1)));
        return month == null ? fallback : (start ? month.atDay(1).toString() : month.atEndOfMonth().toString());
    }

    private int monthNumber(String name) { return java.time.Month.valueOf(name.toUpperCase(Locale.ROOT)).getValue(); }
    private String firstMatch(Pattern pattern, String value, String fallback) { Matcher matcher = pattern.matcher(value.trim()); return matcher.find() ? matcher.group(1).trim() : fallback; }
    private String category(String question, String fallback) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return List.of("Auto & Transport", "Fitness", "Food & Drinks", "Gas & Fuel", "Groceries", "India Remittance", "Income", "Investments", "Rent", "Restaurants", "Shopping", "Transfer", "Travel", "Uncategorized", "Utilities")
                .stream().filter(value -> normalized.contains(value.toLowerCase(Locale.ROOT))).findFirst().orElse(fallback);
    }

    private ConversationContext loadContext(String conversationId) {
        return summaries.findById(conversationId).map(AssistantMemorySummary::getStructuredContext)
                .map(value -> read(value, ConversationContext.class, ConversationContext.empty())).orElse(ConversationContext.empty());
    }

    private ConversationResponse response(AssistantConversation conversation, List<AssistantMessage> history) {
        return new ConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(), conversation.getLastActiveAt(),
                history.stream().map(this::messageResponse).toList());
    }

    private ConversationMessageResponse messageResponse(AssistantMessage message) {
        return new ConversationMessageResponse(message.getRole(), message.getContent(), message.getExecutionMode(),
                read(message.getToolsUsed(), new TypeReference<List<String>>() { }, List.of()),
                read(message.getEvidence(), new TypeReference<List<String>>() { }, List.of()),
                read(message.getAgentSteps(), new TypeReference<List<AgentStep>>() { }, List.of()));
    }

    private AssistantConversation requireConversation(String id) {
        return conversations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
    }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new IllegalStateException("Could not save assistant conversation.", exception); } }
    private <T> T read(String value, Class<T> type, T fallback) { if (value == null || value.isBlank()) return fallback; try { return objectMapper.readValue(value, type); } catch (JsonProcessingException exception) { return fallback; } }
    private <T> T read(String value, TypeReference<T> type, T fallback) { if (value == null || value.isBlank()) return fallback; try { return objectMapper.readValue(value, type); } catch (JsonProcessingException exception) { return fallback; } }

    public record PromptContext(List<AssistantConversationMessage> messages, ConversationContext context) { }
}
