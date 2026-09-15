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
public class BankOfAmericaPdfParser implements StatementTransactionParser {
    private static final Pattern TRANSACTION_START = Pattern.compile("^(\\d{2}/\\d{2}/\\d{2})\\s+(.+)$");
    private static final Pattern AMOUNT_AT_END = Pattern.compile("(-?\\$?\\d{1,3}(?:,\\d{3})*\\.\\d{2})\\s*$");
    private static final Pattern AMOUNT_ONLY = Pattern.compile("^-?\\$?\\d{1,3}(?:,\\d{3})*\\.\\d{2}$");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/uu");

    @Override
    public boolean supports(String text) {
        return text.contains("Bank of America") && text.contains("Deposits and other additions") && text.contains("Withdrawals and other subtractions");
    }

    @Override
    public List<ParsedTransaction> parse(String text) {
        List<ParsedTransaction> results = new ArrayList<>();
        PendingTransaction pending = null;
        boolean inTransactionSection = false;

        for (String rawLine : text.replace("\r", "").split("\n")) {
            String line = rawLine.trim();
            if (line.equals("Deposits and other additions") || line.startsWith("Withdrawals and other subtractions")) {
                inTransactionSection = true;
                continue;
            }
            if (!inTransactionSection || line.isBlank() || line.equals("Date Description Amount") || line.startsWith("Page ") || line.startsWith("continued on")) continue;
            if (line.startsWith("Total deposits") || line.startsWith("Total withdrawals")) {
                addIfComplete(results, pending);
                pending = null;
                continue;
            }

            Matcher start = TRANSACTION_START.matcher(line);
            if (start.matches()) {
                addIfComplete(results, pending);
                pending = new PendingTransaction(LocalDate.parse(start.group(1), DATE_FORMAT), start.group(2));
                pending.extractTrailingAmount();
            } else if (pending != null && AMOUNT_ONLY.matcher(line).matches()) {
                pending.amount = parseAmount(line);
            } else if (pending != null && isStatementFooter(line)) {
                pending.stopCollectingDescription();
            } else if (pending != null && !pending.isDiscardingDescription()) {
                pending.description.append(' ').append(line);
                pending.extractTrailingAmount();
            }
        }
        addIfComplete(results, pending);
        return results;
    }

    @Override
    public String name() { return "Bank of America deposit statement"; }

    private void addIfComplete(List<ParsedTransaction> results, PendingTransaction pending) {
        if (pending != null && pending.amount != null) results.add(new ParsedTransaction(pending.date, pending.description.toString().replaceAll("\\s+", " ").trim(), pending.amount, null));
    }
    private boolean isStatementFooter(String line) {
        return line.startsWith("Can you spot")
                || line.startsWith("Be aware")
                || line.startsWith("Contacted unexpectedly")
                || line.startsWith("Asked to transfer")
                || line.startsWith("Pressured to act")
                || line.startsWith("Share these tips")
                || line.startsWith("When you use the QRC")
                || line.startsWith("Scan this")
                || line.startsWith("Help prevent check fraud")
                || line.startsWith("Consider writing fewer checks")
                || line.startsWith("Instead, pay bills")
                || line.startsWith("You can also set up automatic payments")
                || line.startsWith("Scan the code to learn more")
                || line.startsWith("NEW: BankAmeriDeals")
                || line.startsWith("Find more cash back deals")
                || line.startsWith("Check it out today")
                || line.startsWith("Explore your deals")
                || line.startsWith("Take your security to the next level")
                || line.startsWith("Check your security meter")
                || line.startsWith("To learn more, visit")
                || line.startsWith("Mobile Banking requires")
                || line.startsWith("requires that you download the Mobile Banking app")
                || line.startsWith("Braille")
                || line.startsWith("PRADEEP RAJU")
                || line.startsWith("Account #");
    }
    private BigDecimal parseAmount(String value) { return new BigDecimal(value.replace("$", "").replace(",", "")); }

    private static final class PendingTransaction {
        private final LocalDate date;
        private final StringBuilder description;
        private BigDecimal amount;
        private boolean discardingDescription;
        private PendingTransaction(LocalDate date, String description) { this.date = date; this.description = new StringBuilder(description); }
        private void extractTrailingAmount() { Matcher match = AMOUNT_AT_END.matcher(description); if (match.find()) { amount = new BigDecimal(match.group(1).replace("$", "").replace(",", "")); description.delete(match.start(), description.length()); } }
        private void stopCollectingDescription() { discardingDescription = true; }
        private boolean isDiscardingDescription() { return discardingDescription; }
    }
}
