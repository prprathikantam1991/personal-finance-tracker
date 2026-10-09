package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Parses Bilt activity exports, preserving the exported raw merchant when it is available. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BiltCsvParser implements StatementTransactionParser {
    private static final String HEADER = "Transaction Date,Posted Date,Description,Amount,Card Last 4";

    @Override
    public boolean supports(String text) {
        return text.startsWith(HEADER) && (text.contains("Bilt Rewards") || text.contains("Bilt Housing") || text.contains("BPS*BILT"));
    }

    @Override
    public String name() { return "Bilt activity CSV"; }

    @Override
    public List<ParsedTransaction> parse(String text) {
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length < 2) return List.of();
        List<ParsedTransaction> transactions = new ArrayList<>();
        for (int row = 1; row < lines.length; row++) {
            List<String> values = split(lines[row]);
            if (values.size() < 4) continue;
            try {
                LocalDate date = LocalDate.parse(value(values, 0));
                BigDecimal amount = new BigDecimal(value(values, 3));
                String description = value(values, 2);
                String rawMerchant = value(values, 6);
                if (rawMerchant != null && !rawMerchant.isBlank()) description = rawMerchant;
                String lastFour = value(values, 4);
                if (description != null && !description.isBlank()) {
                    transactions.add(new ParsedTransaction(date, description.trim(), amount, null,
                            lastFour != null && lastFour.matches("\\d{4}") ? lastFour : null));
                }
            } catch (RuntimeException ignored) {
                // A malformed row is skipped; valid Bilt rows remain importable.
            }
        }
        return resolvePaymentAccounts(transactions);
    }

    private String value(List<String> values, int index) { return index < values.size() ? values.get(index).trim() : null; }

    private List<String> split(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') { value.append('"'); index++; }
                else quoted = !quoted;
            } else if (current == ',' && !quoted) { result.add(value.toString()); value.setLength(0); }
            else value.append(current);
        }
        result.add(value.toString());
        return result;
    }

    /** Bilt exports omit the card ending on payment rows; match equal opposite charges where possible. */
    private List<ParsedTransaction> resolvePaymentAccounts(List<ParsedTransaction> rows) {
        List<ParsedTransaction> resolved = new ArrayList<>();
        for (ParsedTransaction row : rows) {
            String lastFour = row.sourceAccountLastFour();
            if (lastFour == null && row.description().toLowerCase().contains("bilt housing")) lastFour = "2658";
            if (lastFour == null && row.description().equalsIgnoreCase("Payment")) {
                lastFour = rows.stream()
                        .filter(candidate -> candidate.sourceAccountLastFour() != null)
                        .filter(candidate -> candidate.amount().abs().compareTo(row.amount().abs()) == 0)
                        .map(ParsedTransaction::sourceAccountLastFour).findFirst().orElse(null);
            }
            resolved.add(new ParsedTransaction(row.date(), row.description(), row.amount(), row.balance(), lastFour));
        }
        return resolved;
    }
}
