package com.pradeep.finance.assistant;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Local LM Studio orchestration. The model can only request named read-only finance tools. */
@Service
public class LocalAssistantService {
    private static final Pattern MONTH_NAME = Pattern.compile("(?i)\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\b");
    private static final Pattern MERCHANT = Pattern.compile("(?i)\\bat\\s+(.+?)(?=\\s+(?:for|in|during)\\b|[?!.]?$)");
    private final FinanceToolsService financeTools;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String model;

    public LocalAssistantService(FinanceToolsService financeTools, ObjectMapper objectMapper,
                                 @Value("${finance.assistant.lm-studio.base-url}") String baseUrl,
                                 @Value("${finance.assistant.lm-studio.model}") String model,
                                 @Value("${finance.assistant.lm-studio.api-key:}") String apiKey,
                                 @Value("${finance.assistant.lm-studio.timeout-ms:30000}") long timeoutMs) {
        this.financeTools = financeTools;
        this.objectMapper = objectMapper;
        this.model = model;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(timeoutMs, 1000)));
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory);
        if (apiKey != null && !apiKey.isBlank()) builder.defaultHeader("Authorization", "Bearer " + apiKey);
        this.restClient = builder.build();
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

    private AssistantChatResponse modelFirstAnswer(String question, List<AssistantConversationMessage> conversation, List<Map<String, Object>> messages, DateRange resolvedRange) {
        JsonNode first = complete(messages, true);
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
        JsonNode finalResponse = complete(messages, false);
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

    private JsonNode complete(List<Map<String, Object>> messages, boolean includeTools) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", 0.1);
        request.put("max_tokens", 700);
        if (includeTools) { request.put("tools", toolDefinitions()); request.put("tool_choice", "auto"); }
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

    private Object execute(String name, JsonNode args) {
        return switch (name) {
            case "get_monthly_summary" -> financeTools.monthlySummary(date(args, "from"), date(args, "to"));
            case "get_category_spending" -> financeTools.categorySpending(date(args, "from"), date(args, "to"));
            case "get_merchant_spending" -> financeTools.merchantSpending(date(args, "from"), date(args, "to"));
            case "get_credit_utilization" -> financeTools.creditUtilization();
            case "get_recurring_activity" -> financeTools.recurringActivity();
            case "compare_periods" -> financeTools.comparePeriods(requiredDate(args, "from"), requiredDate(args, "to"), requiredDate(args, "compareFrom"), requiredDate(args, "compareTo"));
            case "search_transactions" -> financeTools.searchTransactions(text(args, "accountId"), date(args, "from"), date(args, "to"), text(args, "category"), text(args, "merchant"));
            default -> throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The local model requested an unsupported action. Try again, or reload the model in LM Studio.");
        };
    }

    private List<Map<String, Object>> toolDefinitions() {
        return List.of(tool("get_monthly_summary", "Get confirmed income, expenses, remittance, cash flow, and category totals for a date range.", dates()),
                tool("get_category_spending", "Get confirmed spending totals by category for a date range.", dates()),
                tool("get_merchant_spending", "Get the top merchants and spending for a date range.", dates()),
                tool("get_credit_utilization", "Get current combined credit limit, utilization, availability, and prior-statement comparison.", empty()),
                tool("get_recurring_activity", "Get confirmed recurring monthly activity detected from saved transaction history.", empty()),
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
    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException || current instanceof java.net.http.HttpTimeoutException) return true;
            current = current.getCause();
        }
        return false;
    }
    private JsonNode parseArguments(String value) { try { return objectMapper.readTree(value); } catch (JsonProcessingException exception) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The local model returned invalid tool instructions. Try again, or reload the model in LM Studio."); } }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new IllegalStateException("Could not prepare finance tool result.", exception); } }
    private LocalDate date(JsonNode args, String field) { String value = text(args, field); return value == null ? null : LocalDate.parse(value); }
    private LocalDate requiredDate(JsonNode args, String field) { LocalDate value = date(args, field); if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assistant tool needs " + field + "."); return value; }
    private String text(JsonNode args, String field) { String value = args.path(field).asText("").trim(); return value.isBlank() ? null : value; }
}
