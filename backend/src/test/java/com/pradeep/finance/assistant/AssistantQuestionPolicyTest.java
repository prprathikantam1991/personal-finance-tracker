package com.pradeep.finance.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssistantQuestionPolicyTest {

    @Test
    void requiresAPeriodForAnOtherwiseAmbiguousSpendingQuestion() {
        assertThat(AssistantQuestionPolicy.needsSpendingPeriodClarification("How much did I spend?", ConversationContext.empty())).isTrue();
    }

    @Test
    void permitsAnExplicitPeriodOrSavedConversationPeriod() {
        assertThat(AssistantQuestionPolicy.needsSpendingPeriodClarification("How much did I spend last month?", ConversationContext.empty())).isFalse();
        ConversationContext saved = new ConversationContext("2026-08-01", "2026-08-31", null, null, null, null);
        assertThat(AssistantQuestionPolicy.needsSpendingPeriodClarification("How much did I spend?", saved)).isFalse();
    }
}
