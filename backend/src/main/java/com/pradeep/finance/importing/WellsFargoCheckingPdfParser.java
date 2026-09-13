package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WellsFargoCheckingPdfParser implements StatementTransactionParser {
    private static final Pattern START = Pattern.compile("^(\\d{1,2}/\\d{1,2})\\s+(.+)$");
    private static final Pattern MONEY = Pattern.compile("\\d{1,3}(?:,\\d{3})*\\.\\d{2}");
    private static final Pattern STATEMENT_PERIOD = Pattern.compile("(?is)(?:statement period|for the period)\\s*(\\d{1,2}/\\d{1,2}/(?:\\d{2}|\\d{4}))\\s*(?:to|-|–)\\s*(\\d{1,2}/\\d{1,2}/(?:\\d{2}|\\d{4}))");
    @Override public boolean supports(String text) { return text.contains("Wells Fargo Everyday Checking") && text.contains("Transaction history"); }
    @Override public String name() { return "Wells Fargo checking statement"; }
    @Override public List<ParsedTransaction> parse(String text) {
        List<ParsedTransaction> results = new ArrayList<>(); Pending pending = null; boolean inHistory = false; LocalDate periodEnd = statementPeriodEnd(text);
        for (String raw : text.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.equals("Transaction history")) { inHistory = true; continue; }
            if (line.startsWith("Totals ")) { add(results, pending, periodEnd); break; }
            if (!inHistory || line.isBlank() || line.equals("Date") || line.startsWith("Check ") || line.startsWith("Number Description")) continue;
            Matcher start = START.matcher(line);
            if (start.matches()) { add(results, pending, periodEnd); pending = new Pending(start.group(1), start.group(2)); }
            else if (pending != null) {
                List<String> amounts = MONEY.matcher(line).results().map(match -> match.group()).toList();
                if (!amounts.isEmpty()) {
                    BigDecimal amount = new BigDecimal(amounts.get(0).replace(",", ""));
                    if (amounts.size() > 1 && raw.startsWith(" ")) amount = amount.negate();
                    pending.amount = amount;
                } else pending.description.append(' ').append(line);
            }
        }
        return results;
    }
    private void add(List<ParsedTransaction> results, Pending pending, LocalDate periodEnd) { if (pending != null && pending.amount != null) results.add(new ParsedTransaction(transactionDate(pending.date, periodEnd), pending.description.toString().replaceAll("\\s+", " ").trim(), pending.amount, null)); }
    private LocalDate statementPeriodEnd(String text) { Matcher match = STATEMENT_PERIOD.matcher(text); return match.find() ? parsePeriodDate(match.group(2)) : null; }
    private LocalDate transactionDate(String monthDay, LocalDate periodEnd) { int month = Integer.parseInt(monthDay.split("/")[0]); int year = periodEnd == null ? java.time.Year.now().getValue() : periodEnd.getYear() - (month > periodEnd.getMonthValue() ? 1 : 0); return LocalDate.parse(monthDay + "/" + year, DateTimeFormatter.ofPattern("M/d/uuuu")); }
    private LocalDate parsePeriodDate(String value) { return LocalDate.parse(value, DateTimeFormatter.ofPattern(value.length() == 8 ? "M/d/uu" : "M/d/uuuu")); }
    private static final class Pending { private final String date; private final StringBuilder description; private BigDecimal amount; private Pending(String date, String description) { this.date = date; this.description = new StringBuilder(description); } }
}
