package com.pradeep.finance.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ConversationServiceTest {
    private static final String CONVERSATION_ID = "conversation-1";

    private final AssistantConversationRepository conversations = mock(AssistantConversationRepository.class);
    private final AssistantMessageRepository messages = mock(AssistantMessageRepository.class);
    private final AssistantMemorySummaryRepository summaries = mock(AssistantMemorySummaryRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConversationService service;
    private AssistantConversation conversation;

    @BeforeEach
    void setUp() {
        service = new ConversationService(conversations, messages, summaries, objectMapper);
        conversation = AssistantConversation.create();
        conversation.initialize();
        when(conversations.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
    }

    @Test
    void preparesOnlyTheNewestTwelveMessagesAndPersistsTheNewQuestion() {
        List<AssistantMessage> history = IntStream.rangeClosed(1, 13)
                .mapToObj(index -> AssistantMessage.create(CONVERSATION_ID, index, index % 2 == 0 ? "assistant" : "user",
                        "message-" + index, null, null, null, null))
                .toList();
        when(messages.findByConversationIdOrderBySequenceNumberAsc(CONVERSATION_ID)).thenReturn(history);
        when(messages.countByConversationId(CONVERSATION_ID)).thenReturn(13L);
        when(summaries.findById(CONVERSATION_ID)).thenReturn(Optional.of(summary("""
                {"from":"2026-07-01","to":"2026-07-31","merchant":"Patel Brothers","category":"Groceries","accountId":null,"unresolvedQuestion":null}
                """)));

        ConversationService.PromptContext prompt = service.prepareTurn(CONVERSATION_ID, "What about August?");

        assertThat(prompt.messages()).hasSize(12);
        assertThat(prompt.messages().getFirst().text()).isEqualTo("message-2");
        assertThat(prompt.context().merchant()).isEqualTo("Patel Brothers");
        ArgumentCaptor<AssistantMessage> saved = ArgumentCaptor.forClass(AssistantMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo("user");
        assertThat(saved.getValue().getContent()).isEqualTo("What about August?");
    }

    @Test
    void storesExplicitFollowUpContextWithoutReplacingExistingMerchant() throws Exception {
        AssistantMemorySummary summary = summary("""
                {"from":"2026-07-01","to":"2026-07-31","merchant":"Patel Brothers","category":"Groceries","accountId":null,"unresolvedQuestion":null}
                """);
        when(messages.countByConversationId(CONVERSATION_ID)).thenReturn(1L);
        when(summaries.findById(CONVERSATION_ID)).thenReturn(Optional.of(summary));

        service.completeTurn(CONVERSATION_ID, "What about August?", new AssistantChatResponse(
                "August grocery spending was lower.", List.of("get_category_spending"), "test-model", "TOOL_CALL", List.of("Confirmed data")));

        JsonNode context = objectMapper.readTree(summary.getStructuredContext());
        assertThat(context.get("from").asText()).isEqualTo("2026-08-01");
        assertThat(context.get("to").asText()).isEqualTo("2026-08-31");
        assertThat(context.get("merchant").asText()).isEqualTo("Patel Brothers");
        assertThat(context.get("category").asText()).isEqualTo("Groceries");
        ArgumentCaptor<AssistantMessage> saved = ArgumentCaptor.forClass(AssistantMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo("assistant");
    }

    @Test
    void restoresStoredMessageMetadataForTheVisibleTranscript() {
        when(messages.findByConversationIdOrderBySequenceNumberAsc(CONVERSATION_ID)).thenReturn(List.of(
                AssistantMessage.create(CONVERSATION_ID, 1, "assistant", "Grounded answer", "AGENT_RUN",
                        "[\"get_credit_utilization\"]", "[\"Confirmed saved finance data\"]",
                        "[{\"number\":1,\"tool\":\"get_credit_utilization\",\"outcome\":\"Completed\"}]")));

        ConversationResponse restored = service.get(CONVERSATION_ID);

        assertThat(restored.messages()).singleElement().satisfies(message -> {
            assertThat(message.text()).isEqualTo("Grounded answer");
            assertThat(message.toolsUsed()).containsExactly("get_credit_utilization");
            assertThat(message.evidence()).containsExactly("Confirmed saved finance data");
            assertThat(message.steps()).singleElement().extracting(AgentStep::tool).isEqualTo("get_credit_utilization");
        });
    }

    @Test
    void deletesMessagesAndSummaryBeforeTheConversation() {
        service.delete(CONVERSATION_ID);

        InOrder deletion = inOrder(messages, summaries, conversations);
        deletion.verify(messages).deleteByConversationId(CONVERSATION_ID);
        deletion.verify(summaries).deleteById(CONVERSATION_ID);
        deletion.verify(conversations).delete(conversation);
    }

    private AssistantMemorySummary summary(String context) {
        return AssistantMemorySummary.create(CONVERSATION_ID, context);
    }
}
