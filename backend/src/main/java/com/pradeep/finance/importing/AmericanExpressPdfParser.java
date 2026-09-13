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
public class AmericanExpressPdfParser implements StatementTransactionParser {
    private static final Pattern START = Pattern.compile("^(\\d{2}/\\d{2}/\\d{2})\\*?\\s+(.+)$");
    private static final Pattern AMOUNT_END = Pattern.compile("(-?\\$?\\d{1,3}(?:,\\d{3})*\\.\\d{2})\\s*$");
    private static final Pattern AMOUNT_ONLY = Pattern.compile("^-?\\$?\\d{1,3}(?:,\\d{3})*\\.\\d{2}$");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/uu");

    @Override
    public boolean supports(String text) { return text.contains("American Express") && text.contains("New Charges"); }

    @Override
    public List<ParsedTransaction> parse(String text) {
        List<ParsedTransaction> results = new ArrayList<>();
        Pending pending = null;
        boolean inSection = false;
        for (String rawLine : text.replace("\r", "").split("\n")) {
            String line = rawLine.trim();
            if (line.equals("Payments Amount") || line.equals("Credits Amount") || line.equals("Detail")) { inSection = true; continue; }
            if (line.equals("Fees") || line.equals("Interest Charged") || line.startsWith("Total Fees") || line.startsWith("Total Interest")) {
                add(results, pending); pending = null; inSection = false; continue;
            }
            if (!inSection || line.isBlank() || line.equals("Amount") || line.startsWith("Summary") || line.startsWith("Total")) continue;
            Matcher start = START.matcher(line);
            if (start.matches()) {
                add(results, pending);
                pending = new Pending(LocalDate.parse(start.group(1), DATE_FORMAT), start.group(2));
                pending.extractAmount();
            } else if (pending != null && AMOUNT_ONLY.matcher(line).matches()) {
                pending.amount = parseAmount(line);
            } else if (pending != null && !isFooter(line)) {
                pending.description.append(' ').append(line);
                pending.extractAmount();
            }
        }
        add(results, pending);
        return results;
    }

    @Override
    public String name() { return "American Express credit-card statement"; }

    private void add(List<ParsedTransaction> results, Pending pending) {
        if (pending != null && pending.amount != null) results.add(new ParsedTransaction(pending.date, pending.description.toString().replaceAll("\\s+", " ").trim(), pending.amount, null));
    }
    private boolean isFooter(String line) { return line.startsWith("p. ") || line.startsWith("Continued on") || line.startsWith("PRADEEP ") || line.startsWith("Card Ending"); }
    private BigDecimal parseAmount(String value) { return new BigDecimal(value.replace("$", "").replace(",", "")); }
    private static final class Pending {
        private final LocalDate date; private final StringBuilder description; private BigDecimal amount;
        private Pending(LocalDate date, String description) { this.date = date; this.description = new StringBuilder(description); }
        private void extractAmount() { Matcher match = AMOUNT_END.matcher(description); if (match.find()) { amount = new BigDecimal(match.group(1).replace("$", "").replace(",", "")); description.delete(match.start(), description.length()); } }
    }
}
