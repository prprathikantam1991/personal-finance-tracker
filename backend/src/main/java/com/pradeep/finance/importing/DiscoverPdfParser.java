package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class DiscoverPdfParser implements StatementTransactionParser {
    private static final Pattern START = Pattern.compile("^(\\d{2}/\\d{2})\\s+(.+)$");
    private static final Pattern AMOUNT_END = Pattern.compile("(-?\\$?\\d{1,3}(?:,\\d{3})*\\.\\d{2})\\s*$");
    private static final Pattern AMOUNT_ONLY = Pattern.compile("^-?\\$?\\d{1,3}(?:,\\d{3})*\\.\\d{2}$");

    @Override public boolean supports(String text) { return text.contains("DISCOVER IT CARD") && text.contains("PURCHASES MERCHANT CATEGORY"); }
    @Override public String name() { return "Discover credit-card statement"; }

    @Override
    public List<ParsedTransaction> parse(String text) {
        List<ParsedTransaction> results = new ArrayList<>();
        Pending pending = null;
        boolean inTransactions = false;
        for (String rawLine : text.replace("\r", "").split("\n")) {
            String line = rawLine.trim();
            if (line.equals("ONLINE PHONE PAYMENTS")) { inTransactions = true; continue; }
            if (line.startsWith("PREVIOUS BALANCE")) { add(results, pending); break; }
            if (!inTransactions || line.isBlank() || isHeader(line)) continue;
            Matcher start = START.matcher(line);
            if (start.matches()) {
                add(results, pending);
                pending = new Pending(parseDate(start.group(1)), start.group(2));
                pending.extractAmount();
            } else if (pending != null && AMOUNT_ONLY.matcher(line).matches()) {
                pending.amount = parseAmount(line);
            } else if (pending != null && !isFooter(line)) {
                pending.description.append(' ').append(line);
                pending.extractAmount();
            }
        }
        return results;
    }

    private LocalDate parseDate(String value) { return LocalDate.parse(value + "/" + Year.now().getValue(), DateTimeFormatter.ofPattern("MM/dd/uuuu")); }
    private void add(List<ParsedTransaction> results, Pending pending) { if (pending != null && pending.amount != null) results.add(new ParsedTransaction(pending.date, pending.description.toString().replaceAll("\\s+", " ").trim(), pending.amount, null)); }
    private boolean isHeader(String line) { return line.equals("TRANS.") || line.equals("DATE PAYMENTS AND CREDITS AMOUNT") || line.equals("DATE PURCHASES MERCHANT CATEGORY AMOUNT") || line.startsWith("2026 TOTALS") || line.equals("RewardsTransactions"); }
    private boolean isFooter(String line) { return line.startsWith("Page ") || line.startsWith("OPEN TO CLOSE") || line.startsWith("Cashback Bonus") || line.startsWith("TOTAL "); }
    private BigDecimal parseAmount(String value) { return new BigDecimal(value.replace("$", "").replace(",", "")); }
    private static final class Pending { private final LocalDate date; private final StringBuilder description; private BigDecimal amount; private Pending(LocalDate date, String description) { this.date = date; this.description = new StringBuilder(description); } private void extractAmount() { Matcher match = AMOUNT_END.matcher(description); if (match.find()) { amount = new BigDecimal(match.group(1).replace("$", "").replace(",", "")); description.delete(match.start(), description.length()); } } }
}
