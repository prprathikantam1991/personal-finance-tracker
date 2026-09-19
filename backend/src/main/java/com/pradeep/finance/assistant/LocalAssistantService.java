package com.pradeep.finance.assistant;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Local LM Studio orchestration. The model can only request named read-only finance tools. */
@Service
public class LocalAssistantService {
    private static final Pattern MONTH_NAME = Pattern.compile("(?i)\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\b");
    private static final Pattern MERCHANT = Pattern.compile("(?i)\\bat\\s+(.+?)(?=\\s+(?:for|in|during)\\b|[?!.]?$)");
    private final FinanceToolsService financeTools;
    private final ObjectMapper objectMapper;
    private final LocalModelClient localModelClient;
    private final String model;

    public LocalAssistantService(FinanceToolsService financeTools, ObjectMapper objectMapper, LocalModelClient localModelClient,
                                 @Value("${finance.assistant.model}") String model) {
        this.financeTools = financeTools;
        this.objectMapper = objectMapper;
        this.localModelClient = localModelClient;
        this.model = model;
    }

    public AssistantChatResponse chat(String question, List<AssistantConversationMessage> conversation) {
        List<AssistantConversationMessage> safeConversation = conversation == null ? List.of() : conversation;
        DateRange resolvedRange = resolveRange(question, safeConversation);
        String resolvedMerchant = resolveMerchant(question, safeConversation);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt() + contextPrompt(resolvedRange, resolvedMerchant)));
        safeConversation.stream().filter(item -> ("user".equals(item.role()) || "assistant".equals(item.role())) && item.text() != null && !item.text().isBlank()).limit(12).forEach(item -> messages.add(message(item.role(), item.text())));
        messages.add(message("user", question.trim()));
        try {
            return modelFirstAnswer(question, safeConversation, messages, resolvedRange);
        } catch (ResponseStatusException exception) {
            return directAnswer(question, safeConversation)
                    .orElseThrow(() -> exception);
        }
    }

    /**
     * V4's bounded multi-step loop. One tool is permitted per model round so each
     * financial lookup is validated and recorded before the next decision is made.
     */
    public AgentRunResponse agentRun(String question, List<AssistantConversationMessage> conversation) {
        List<AssistantConversationMessage> safeConversation = conversation == null ? List.of() : conversation;
        DateRange resolvedRange = resolveRange(question, safeConversation);
        String resolvedMerchant = resolveMerchant(question, safeConversation);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt() + contextPrompt(resolvedRange, resolvedMerchant)
                + " For an agent run, request at most one finance tool at a time. After each tool result, decide whether another allowed tool is needed or answer."
                + " When a resolved period is provided, use it for every period-sensitive lookup. Do not request data that an earlier tool result already provided."));
        safeConversation.stream().filter(item -> ("user".equals(item.role()) || "assistant".equals(item.role())) && item.text() != null && !item.text().isBlank()).limit(12).forEach(item -> messages.add(message(item.role(), item.text())));
        messages.add(message("user", question.trim()));

        List<String> toolsUsed = new ArrayList<>();
        List<AgentStep> steps = new ArrayList<>();
        Set<String> completedCalls = new java.util.HashSet<>();
        for (int round = 1; round <= 3; round++) {
            JsonNode modelResponse = complete(messages, true, 160);
            JsonNode assistantMessage = modelResponse.path("choices").path(0).path("message");
            List<JsonNode> calls = new ArrayList<>();
            assistantMessage.path("tool_calls").forEach(calls::add);
            if (calls.isEmpty()) {
                if (toolsUsed.isEmpty()) {
                    Optional<AssistantChatResponse> fallback = directAnswer(question, safeConversation);
                    if (fallback.isPresent()) {
                        AssistantChatResponse answer = fallback.get();
                        List<AgentStep> fallbackSteps = answer.toolsUsed().stream()
                                .map(tool -> new AgentStep(1, tool, "Fallback"))
                                .toList();
                        return new AgentRunResponse(answer.answer(), answer.toolsUsed(), fallbackSteps,
                                "FALLBACK", model, evidence(resolvedRange, answer.toolsUsed()));
                    }
                    return agentStopped("The local model did not request finance data for this question. Try a more specific question or use a tool-capable model in LM Studio.",
                            toolsUsed, steps, resolvedRange, "NO_TOOL_REQUESTED");
                }
                return new AgentRunResponse(content(assistantMessage), List.copyOf(toolsUsed), List.copyOf(steps), "COMPLETED", model, evidence(resolvedRange, toolsUsed));
            }
            if (calls.size() != 1) return agentStopped("The local model requested more than one tool at once. Please try the question again.", toolsUsed, steps, resolvedRange, "MULTIPLE_TOOLS_REQUESTED");

            JsonNode call = calls.getFirst();
            String name = call.path("function").path("name").asText();
            try {
                JsonNode suppliedArguments = parseArguments(call.path("function").path("arguments").asText("{}"));
                if (isUndatedRepeat(name, suppliedArguments, toolsUsed)) {
                    JsonNode finalResponse = complete(messages, false, 500);
                    return new AgentRunResponse(content(finalResponse.path("choices").path(0).path("message")), List.copyOf(toolsUsed), List.copyOf(steps), "COMPLETED", model, evidence(resolvedRange, toolsUsed));
                }
                JsonNode arguments = normalizeArguments(name, suppliedArguments, resolvedRange);
                validateAgentCall(name, arguments);
                if (!completedCalls.add(name + ":" + json(arguments))) {
                    JsonNode finalResponse = complete(messages, false, 500);
                    return new AgentRunResponse(content(finalResponse.path("choices").path(0).path("message")), List.copyOf(toolsUsed), List.copyOf(steps), "COMPLETED", model, evidence(resolvedRange, toolsUsed));
                }
                Object result = execute(name, arguments);
                String resultJson = json(result);
                if (resultJson.length() > 24_000) return agentStopped("The requested finance result was too large to use safely. Please narrow the question.", toolsUsed, steps, resolvedRange, "RESULT_TOO_LARGE");
                toolsUsed.add(name);
                steps.add(new AgentStep(round, name, "Completed"));
                messages.add(objectMapper.convertValue(assistantMessage, Map.class));
                messages.add(Map.of("role", "tool", "tool_call_id", call.path("id").asText(), "content", resultJson));
            } catch (ResponseStatusException exception) {
                steps.add(new AgentStep(round, name.isBlank() ? "unknown" : name, "Rejected"));
                return agentStopped(exception.getReason(), toolsUsed, steps, resolvedRange, "VALIDATION_STOP");
            }
        }
        JsonNode finalResponse = complete(messages, false, 500);
        return new AgentRunResponse(content(finalResponse.path("choices").path(0).path("message")), List.copyOf(toolsUsed), List.copyOf(steps), "TOOL_BUDGET_REACHED", model, evidence(resolvedRange, toolsUsed));
    }

    private AgentRunResponse agentStopped(String answer, List<String> tools, List<AgentStep> steps, DateRange range, String stopReason) {
        return new AgentRunResponse(answer == null || answer.isBlank() ? "The agent run stopped safely before producing an answer." : answer,
                List.copyOf(tools), List.copyOf(steps), stopReason, model, evidence(range, tools));
    }

    private AssistantChatResponse modelFirstAnswer(String question, List<AssistantConversationMessage> conversation, List<Map<String, Object>> messages, DateRange resolvedRange) {
        JsonNode first = complete(messages, true, 160);
        JsonNode assistantMessage = first.path("choices").path(0).path("message");
        List<JsonNode> calls = new ArrayList<>();
        assistantMessage.path("tool_calls").forEach(calls::add);
        if (calls.isEmpty()) {
            return directAnswer(question, conversation)
                    .orElse(new AssistantChatResponse(content(assistantMessage), List.of(), model, "MODEL_RESPONSE", List.of("No finance-data tool was used for this response.")));
        }

        messages.add(objectMapper.convertValue(assistantMessage, Map.class));
        List<String> toolsUsed = new ArrayList<>();
        for (JsonNode call : calls) {
            String name = call.path("function").path("name").asText();
            JsonNode arguments = parseArguments(call.path("function").path("arguments").asText("{}"));
            Object result = execute(name, arguments);
            toolsUsed.add(name);
            messages.add(Map.of("role", "tool", "tool_call_id", call.path("id").asText(), "content", json(result)));
        }
        JsonNode finalResponse = complete(messages, false, 500);
        return new AssistantChatResponse(content(finalResponse.path("choices").path(0).path("message")), List.copyOf(toolsUsed), model, "MODEL_TOOL_CALL", evidence(resolvedRange, toolsUsed));
    }

    private Optional<AssistantChatResponse> directAnswer(String question, List<AssistantConversationMessage> conversation) {
        String normalized = question.toLowerCase(Locale.ROOT);
        DateRange range = resolveRange(question, conversation);
        if (normalized.contains("credit utilization")) {
            FinanceToolsService.CreditUtilization data = financeTools.creditUtilization();
            boolean perCard = normalized.contains("each card") || normalized.contains("by card") || normalized.contains("per card");
            String answer = perCard ? cardUtilizationAnswer(data) : "Your overall credit utilization is " + percent(data.utilizationPercent()) + ". This is based on " + money(data.utilized()) + " utilized out of " + money(data.totalLimit()) + " in total credit limit.";
            return Optional.of(new AssistantChatResponse(answer, List.of("get_credit_utilization"), model, "FALLBACK", evidence(null, List.of("get_credit_utilization"))));
        }
        if (normalized.contains("spend by category") || normalized.contains("spending by category")) {
            if (range == null && normalized.contains("last month")) range = lastMonth();
            if (range != null) {
                List<com.pradeep.finance.dashboard.DashboardSummary.CategoryTotal> categories = financeTools.categorySpending(range.from(), range.to());
                String items = categories.isEmpty() ? "No confirmed spending was found." : categories.stream().map(item -> item.category() + ": " + money(item.amount())).reduce((left, right) -> left + "\n- " + right).orElse("");
                return Optional.of(new AssistantChatResponse("Confirmed spending by category for " + range.label() + ":\n- " + items, List.of("get_category_spending"), model, "FALLBACK", evidence(range, List.of("get_category_spending"))));
            }
        }
        String merchant = resolveMerchant(question, conversation);
        if (merchant != null) {
            List<com.pradeep.finance.transaction.TransactionResponse> transactions = financeTools.searchTransactions(null, range == null ? null : range.from(), range == null ? null : range.to(), null, merchant);
            BigDecimal spending = transactions.stream().map(item -> item.accountType() == com.pradeep.finance.account.AccountType.CREDIT_CARD ? item.amount() : item.amount().negate()).filter(amount -> amount.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
            String period = range == null ? "across all saved history" : "for " + range.label();
            String answer = transactions.isEmpty() ? "I found no confirmed transactions for " + merchant + " " + period + "." : "You spent " + money(spending) + " at " + merchant + " " + period + " across " + transactions.size() + " transaction" + (transactions.size() == 1 ? "." : "s.");
            return Optional.of(new AssistantChatResponse(answer, List.of("search_transactions"), model, "FALLBACK", evidence(range, List.of("search_transactions"))));
        }
        return Optional.empty();
    }

    private String systemPrompt() {
        DateRange lastMonth = lastMonth();
        return "You are the Personal Finance Tracker assistant. For factual finance questions, use available finance tools before answering. Use only tool results for facts. Today is " + LocalDate.now() + ". 'Last month' means " + lastMonth.label() + "; do not ask the user to provide those dates. 'Overall' means all saved history unless the question is about credit utilization. When a month name has no year, use the current year. Never invent transactions, values, dates, or financial advice. Tools are read-only.";
    }
    private String contextPrompt(DateRange range, String merchant) {
        List<String> context = new ArrayList<>();
        if (range != null) context.add("resolved period: " + range.label());
        if (merchant != null) context.add("resolved merchant: " + merchant);
        return context.isEmpty() ? "" : " Current conversation context: " + String.join("; ", context) + ". Use it only when it directly resolves the user's follow-up.";
    }
    private List<String> evidence(DateRange range, List<String> tools) {
        List<String> result = new ArrayList<>();
        result.add("Confirmed saved finance data");
        if (range != null) result.add("Period: " + range.label());
        if (!tools.isEmpty()) result.add("Tool: " + String.join(", ", tools));
        return List.copyOf(result);
    }
    private DateRange lastMonth() { YearMonth month = YearMonth.now().minusMonths(1); return new DateRange(month.atDay(1), month.atEndOfMonth()); }
    private DateRange resolveRange(String question, List<AssistantConversationMessage> conversation) {
        DateRange explicit = dateRange(question);
        if (explicit != null) return explicit;
        if (question.toLowerCase(Locale.ROOT).contains("last month")) return lastMonth();
        if (!usesPriorContext(question)) return null;
        return conversation.stream().filter(item -> "user".equals(item.role())).map(item -> dateRange(item.text())).filter(java.util.Objects::nonNull).reduce((first, second) -> second).orElse(null);
    }
    private DateRange dateRange(String question) {
        Matcher matcher = MONTH_NAME.matcher(question);
        List<Integer> months = new ArrayList<>(); while (matcher.find()) months.add(monthNumber(matcher.group(1)));
        if (months.isEmpty()) return null;
        int year = LocalDate.now().getYear(); YearMonth first = YearMonth.of(year, months.getFirst()); YearMonth last = YearMonth.of(year, months.getLast());
        return new DateRange(first.atDay(1), last.atEndOfMonth());
    }
    private int monthNumber(String name) { return java.time.Month.valueOf(name.toUpperCase(Locale.ROOT)).getValue(); }
    private String merchant(String question) { Matcher matcher = MERCHANT.matcher(question.trim()); return matcher.find() ? matcher.group(1).trim() : null; }
    private String resolveMerchant(String question, List<AssistantConversationMessage> conversation) {
        String explicit = merchant(question);
        if (explicit != null || !usesPriorContext(question)) return explicit;
        return conversation.stream().filter(item -> "user".equals(item.role())).map(item -> merchant(item.text())).filter(java.util.Objects::nonNull).reduce((first, second) -> second).orElse(null);
    }
    private boolean usesPriorContext(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.startsWith("overall") || normalized.contains("what about") || normalized.contains("same period") || normalized.contains("that period") || normalized.contains("that merchant");
    }
    private String cardUtilizationAnswer(FinanceToolsService.CreditUtilization data) {
        String cards = data.cards().stream().map(card -> card.accountName() + ": " + percent(card.utilizationPercent()) + " used (" + money(card.statementBalance()) + " of " + money(card.creditLimit()) + ")").reduce((left, right) -> left + "\n- " + right).orElse("No card snapshots are available.");
        return "Your overall credit utilization is " + percent(data.utilizationPercent()) + ". By card:\n- " + cards;
    }
    private String money(BigDecimal value) { return NumberFormat.getCurrencyInstance(Locale.US).format(value == null ? BigDecimal.ZERO : value); }
    private String percent(BigDecimal value) { return value == null ? "not available" : value.stripTrailingZeros().toPlainString() + "%"; }
    private record DateRange(LocalDate from, LocalDate to) { String label() { return from + " through " + to; } }

    /** Tool-choice turns need compact structured output; reserve the larger budget for the final explanation. */
    private JsonNode complete(List<Map<String, Object>> messages, boolean includeTools, int maxTokens) {
        return localModelClient.complete(messages, includeTools ? toolDefinitions() : null, maxTokens);
    }

    private Object execute(String name, JsonNode args) {
        return switch (name) {
            case "get_monthly_summary" -> financeTools.monthlySummary(date(args, "from"), date(args, "to"));
            case "get_category_spending" -> financeTools.categorySpending(date(args, "from"), date(args, "to"));
            case "get_merchant_spending" -> financeTools.merchantSpending(date(args, "from"), date(args, "to"));
            case "get_credit_utilization" -> financeTools.creditUtilization();
            case "get_recurring_activity" -> financeTools.recurringActivity();
            case "get_account_overview" -> financeTools.accountOverview();
            case "get_account_history" -> financeTools.accountHistory(requiredText(args, "accountId"));
            case "compare_periods" -> financeTools.comparePeriods(requiredDate(args, "from"), requiredDate(args, "to"), requiredDate(args, "compareFrom"), requiredDate(args, "compareTo"));
            case "search_transactions" -> financeTools.searchTransactions(text(args, "accountId"), date(args, "from"), date(args, "to"), text(args, "category"), text(args, "merchant"));
            default -> throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The local model requested an unsupported action. Try again, or reload the model in LM Studio.");
        };
    }

    private void validateAgentCall(String name, JsonNode args) {
        Set<String> tools = Set.of("get_monthly_summary", "get_category_spending", "get_merchant_spending", "get_credit_utilization", "get_recurring_activity", "get_account_overview", "get_account_history", "compare_periods", "search_transactions");
        if (!tools.contains(name)) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The local model requested an unsupported action.");
        if (!args.isObject()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The local model supplied invalid tool arguments.");
        // All tools are read-only and execute only their documented fields. Some models add
        // harmless presentation hints such as "limit"; ignoring those lets a valid multi-step
        // plan continue without allowing the hint to alter the underlying data lookup.
        validateAgentDates(args, "from", "to");
        validateAgentDates(args, "compareFrom", "compareTo");
    }

    /**
     * Models sometimes add UI-only fields (for example, a desired result limit) or omit dates
     * already resolved from the user's request. Project to the read-only tool schema before
     * execution so those hints cannot widen or otherwise change a data lookup.
     */
    private JsonNode normalizeArguments(String name, JsonNode supplied, DateRange resolvedRange) {
        if (!supplied.isObject()) return supplied;
        Set<String> allowedFields = switch (name) {
            case "get_monthly_summary", "get_category_spending", "get_merchant_spending" -> Set.of("from", "to");
            case "get_account_history" -> Set.of("accountId");
            case "compare_periods" -> Set.of("from", "to", "compareFrom", "compareTo");
            case "search_transactions" -> Set.of("accountId", "from", "to", "category", "merchant");
            default -> Set.of();
        };
        ObjectNode normalized = objectMapper.createObjectNode();
        allowedFields.forEach(field -> {
            if (supplied.has(field)) normalized.set(field, supplied.get(field));
        });
        if (resolvedRange != null && usesDateRange(name)) {
            if (!normalized.has("from")) normalized.put("from", resolvedRange.from().toString());
            if (!normalized.has("to")) normalized.put("to", resolvedRange.to().toString());
        }
        return normalized;
    }

    private boolean usesDateRange(String name) {
        return Set.of("get_monthly_summary", "get_category_spending", "get_merchant_spending", "search_transactions").contains(name);
    }

    private boolean isUndatedRepeat(String name, JsonNode suppliedArguments, List<String> toolsUsed) {
        return usesDateRange(name) && toolsUsed.contains(name) && suppliedArguments.isObject()
                && !suppliedArguments.has("from") && !suppliedArguments.has("to");
    }

    private void validateAgentDates(JsonNode args, String fromField, String toField) {
        LocalDate from = agentDate(args, fromField);
        LocalDate to = agentDate(args, toField);
        if (from != null && to != null && (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 731)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assistant date range must be between zero and 731 days.");
        }
    }

    private LocalDate agentDate(JsonNode args, String field) {
        String value = text(args, field);
        if (value == null) return null;
        try { return LocalDate.parse(value); }
        catch (java.time.format.DateTimeParseException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assistant dates must use YYYY-MM-DD."); }
    }

    private List<Map<String, Object>> toolDefinitions() {
        return List.of(tool("get_monthly_summary", "Get confirmed income, expenses, remittance, cash flow, and category totals for a date range.", dates()),
                tool("get_category_spending", "Get confirmed spending totals by category for a date range.", dates()),
                tool("get_merchant_spending", "Get the top merchants and spending for a date range.", dates()),
                tool("get_credit_utilization", "Get current combined credit limit, utilization, availability, and prior-statement comparison.", empty()),
                tool("get_recurring_activity", "Get confirmed recurring monthly activity detected from saved transaction history.", empty()),
                tool("get_account_overview", "List compact current snapshots for every saved account, including account IDs, balances, card limits, utilization, APR, due dates, and statement dates.", empty()),
                tool("get_account_history", "Get up to 12 statement snapshots for one account. Call get_account_overview first when an account ID is needed.", Map.of("type", "object", "properties", Map.of("accountId", stringProperty()), "required", List.of("accountId"))),
                tool("compare_periods", "Compare two explicit date ranges for income, expenses, remittance, cash flow, and category totals.", Map.of("type", "object", "properties", Map.of("from", dateProperty(), "to", dateProperty(), "compareFrom", dateProperty(), "compareTo", dateProperty()), "required", List.of("from", "to", "compareFrom", "compareTo"))),
                tool("search_transactions", "Find confirmed transactions by optional date range, account, category, or merchant.", Map.of("type", "object", "properties", Map.of("accountId", stringProperty(), "from", dateProperty(), "to", dateProperty(), "category", stringProperty(), "merchant", stringProperty()))));
    }
    private Map<String, Object> tool(String name, String description, Map<String, Object> parameters) { return Map.of("type", "function", "function", Map.of("name", name, "description", description, "parameters", parameters)); }
    private Map<String, Object> dates() { return Map.of("type", "object", "properties", Map.of("from", dateProperty(), "to", dateProperty())); }
    private Map<String, Object> empty() { return Map.of("type", "object", "properties", Map.of()); }
    private Map<String, Object> dateProperty() { return Map.of("type", "string", "description", "Date in YYYY-MM-DD format"); }
    private Map<String, Object> stringProperty() { return Map.of("type", "string"); }
    private Map<String, Object> message(String role, String content) { return Map.of("role", role, "content", content); }
    private String content(JsonNode message) { String value = message.path("content").asText("").trim(); return value.isBlank() ? "I could not produce an answer from the available local data." : value; }
    private JsonNode parseArguments(String value) { try { return objectMapper.readTree(value); } catch (JsonProcessingException exception) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The local model returned invalid tool instructions. Try again, or reload the model in LM Studio."); } }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new IllegalStateException("Could not prepare finance tool result.", exception); } }
    private LocalDate date(JsonNode args, String field) { String value = text(args, field); return value == null ? null : LocalDate.parse(value); }
    private LocalDate requiredDate(JsonNode args, String field) { LocalDate value = date(args, field); if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assistant tool needs " + field + "."); return value; }
    private String text(JsonNode args, String field) { String value = args.path(field).asText("").trim(); return value.isBlank() ? null : value; }
    private String requiredText(JsonNode args, String field) { String value = text(args, field); if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assistant tool needs " + field + "."); return value; }
}
