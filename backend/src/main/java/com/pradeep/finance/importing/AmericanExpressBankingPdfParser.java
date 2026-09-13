package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** Parses American Express National Bank checking and savings statements. */
@Component
public class AmericanExpressBankingPdfParser implements StatementTransactionParser {
    private static final Pattern START = Pattern.compile("^(\\d{2}/\\d{2}/\\d{4})\\s+(.+)$");
    private static final Pattern AMOUNT = Pattern.compile("-?\\$\\d{1,3}(?:,\\d{3})*\\.\\d{2}");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/uuuu");

    @Override
    public boolean supports(String text) {
        return text.contains("American Express National Bank") && text.contains("Account Activity")
                && (text.contains("Rewards Checking") || text.contains("High Yield Savings Account"));
    }

    @Override
    public List<ParsedTransaction> parse(String text) {
        List<ParsedTransaction> results = new ArrayList<>();
        Pending pending = null;
        boolean inActivity = false;
        for (String rawLine : text.replace("\r", "").split("\n")) {
            String line = rawLine.trim();
            if (line.equals("Account Activity")) {
                inActivity = true;
                continue;
            }
            if (!inActivity || line.isBlank() || line.startsWith("Date ") || line.equals("Date")) continue;
            if (line.matches("^\\d{6}.*") || line.startsWith("Account Statement For:")) {
                add(results, pending);
                break;
            }
            Matcher start = START.matcher(line);
            if (start.matches()) {
                add(results, pending);
                pending = new Pending(LocalDate.parse(start.group(1), DATE_FORMAT), start.group(2));
            } else if (pending != null && !isFooter(line)) {
                pending.description.append(' ').append(line);
            }
        }
        add(results, pending);
        return results;
    }

    @Override
    public String name() {
        return "American Express banking statement";
    }

    private void add(List<ParsedTransaction> results, Pending pending) {
        if (pending == null || pending.isBalanceRow()) return;
        Matcher amounts = AMOUNT.matcher(pending.description);
        if (!amounts.find()) return;
        BigDecimal amount = new BigDecimal(amounts.group().replace("$", "").replace(",", ""));
        if (pending.isDebit() && amount.signum() > 0) amount = amount.negate();
        String description = AMOUNT.matcher(pending.description).replaceAll("").replaceAll("\\s+", " ").trim();
        results.add(new ParsedTransaction(pending.date, description, amount, null));
    }

    private boolean isFooter(String line) {
        return line.startsWith("Page ") || line.startsWith("American Express National Bank")
                || line.startsWith("Accounts offered by") || line.startsWith("Account Owner")
                || line.startsWith("Continued on") || line.startsWith("PRADEEP ")
                || line.startsWith("Statement Date:") || line.startsWith("Account Ending:")
                || line.startsWith("Account Name:") || line.startsWith("p. ");
    }

    private static final class Pending {
        private final LocalDate date;
        private final StringBuilder description;

        private Pending(LocalDate date, String description) {
            this.date = date;
            this.description = new StringBuilder(description);
        }

        private boolean isBalanceRow() {
            String value = description.toString().toLowerCase(Locale.ROOT);
            return value.startsWith("beginning balance") || value.startsWith("ending balance");
        }

        private boolean isDebit() {
            String value = description.toString().toLowerCase(Locale.ROOT);
            return value.contains("withdrawal") || value.contains(" transfer debit")
                    || value.contains("requested transfer to") || value.contains("payment: debit");
        }
    }
}
