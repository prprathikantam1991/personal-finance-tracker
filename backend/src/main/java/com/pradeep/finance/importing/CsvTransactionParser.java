package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class CsvTransactionParser implements StatementTransactionParser {
    @Override
    public boolean supports(String text) {
        return text.lines().findFirst().map(line -> line.contains(",")).orElse(false);
    }

    @Override
    public String name() { return "Generic CSV"; }
    public List<ParsedTransaction> parse(String text) {
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length < 2) return List.of();
        List<String> headers = split(lines[0]).stream().map(this::normalize).toList();
        int dateIndex = indexOf(headers, "date", "transaction date", "posting date");
        int descriptionIndex = indexOf(headers, "description", "merchant", "details", "memo");
        int amountIndex = indexOf(headers, "amount");
        int debitIndex = indexOf(headers, "debit", "withdrawal");
        int creditIndex = indexOf(headers, "credit", "deposit");
        int balanceIndex = indexOf(headers, "balance", "running balance");
        if (dateIndex < 0 || descriptionIndex < 0 || (amountIndex < 0 && debitIndex < 0 && creditIndex < 0)) return List.of();

        List<ParsedTransaction> transactions = new ArrayList<>();
        for (int row = 1; row < lines.length; row++) {
            List<String> values = split(lines[row]);
            LocalDate date = valueAt(values, dateIndex) == null ? null : parseDate(valueAt(values, dateIndex));
            String description = valueAt(values, descriptionIndex);
            BigDecimal amount = amountIndex >= 0 ? parseAmount(valueAt(values, amountIndex))
                    : subtract(parseAmount(valueAt(values, creditIndex)), parseAmount(valueAt(values, debitIndex)));
            if (date != null && description != null && !description.isBlank() && amount != null) {
                transactions.add(new ParsedTransaction(date, description.trim(), amount, parseAmount(valueAt(values, balanceIndex))));
            }
        }
        return transactions;
    }

    private int indexOf(List<String> headers, String... options) { for (String option : options) { int index = headers.indexOf(option); if (index >= 0) return index; } return -1; }
    private String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private String valueAt(List<String> values, int index) { return index >= 0 && index < values.size() ? values.get(index).trim() : null; }
    private BigDecimal subtract(BigDecimal credit, BigDecimal debit) { if (credit == null && debit == null) return null; return (credit == null ? BigDecimal.ZERO : credit).subtract(debit == null ? BigDecimal.ZERO : debit); }
    private BigDecimal parseAmount(String value) { if (value == null || value.isBlank()) return null; try { boolean negative = value.contains("(") || value.contains("-"); String cleaned = value.replaceAll("[^0-9.]", ""); if (cleaned.isBlank()) return null; BigDecimal amount = new BigDecimal(cleaned); return negative ? amount.negate() : amount; } catch (NumberFormatException e) { return null; } }
    private LocalDate parseDate(String value) { for (DateTimeFormatter format : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("M/d/uuuu"), DateTimeFormatter.ofPattern("M/d/uu"))) { try { return LocalDate.parse(value.trim(), format); } catch (DateTimeParseException ignored) { } } return null; }
    private List<String> split(String line) { List<String> result = new ArrayList<>(); StringBuilder value = new StringBuilder(); boolean quoted = false; for (int i = 0; i < line.length(); i++) { char current = line.charAt(i); if (current == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { value.append('"'); i++; } else quoted = !quoted; } else if (current == ',' && !quoted) { result.add(value.toString()); value.setLength(0); } else value.append(current); } result.add(value.toString()); return result; }
}
