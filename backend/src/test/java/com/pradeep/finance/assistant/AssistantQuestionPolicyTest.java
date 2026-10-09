package com.pradeep.finance.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssistantQuestionPolicyTest {

    @Test
    void requiresAPeriodForAnOtherwiseAmbiguousSpendingQuestion() {
        assertThat(AssistantQuestionPolicy.needsSpendingPeriodClarification("How much did I spend?")).isTrue();
    }

    @Test
    void permitsAnExplicitPeriod() {
        assertThat(AssistantQuestionPolicy.needsSpendingPeriodClarification("How much did I spend last month?")).isFalse();
    }
}
