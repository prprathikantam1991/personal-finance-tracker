package com.pradeep.finance.assistant;

import java.util.Locale;

/**
 * Deterministic guardrails for questions where guessing a date range would make
 * a finance answer misleading. The model still handles normal language and
 * tool selection once the user supplies the missing period.
 */
final class AssistantQuestionPolicy {
    private AssistantQuestionPolicy() {
    }

    static boolean needsSpendingPeriodClarification(String question) {
        if (question == null || question.isBlank()) return false;
        String normalized = question.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        boolean asksForAmount = normalized.contains("how much") || normalized.startsWith("what did i ");
        boolean mentionsSpending = normalized.matches(".*\\b(spend|spent|spending|expense|expenses)\\b.*");
        return asksForAmount && mentionsSpending && !hasExplicitPeriod(normalized);
    }

    private static boolean hasExplicitPeriod(String question) {
        return question.matches(".*\\b(today|yesterday|week|month|year|quarter|last|this|current|overall|all|history|since|between|from|to|during|january|february|march|april|may|june|july|august|september|october|november|december|20\\d{2})\\b.*");
    }
}
