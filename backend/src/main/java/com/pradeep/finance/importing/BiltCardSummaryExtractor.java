package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class BiltCardSummaryExtractor implements StatementSummaryExtractor {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US);
    private static final Pattern BALANCE = Pattern.compile("New balance as of ([A-Z][a-z]{2} \\d{1,2}, \\d{4})\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern LIMIT = Pattern.compile("Credit limit\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern AVAILABLE = Pattern.compile("Available credit\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern MINIMUM = Pattern.compile("Minimum payment due\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern DUE = Pattern.compile("Payment due by\\s+([A-Z][a-z]{2} \\d{1,2}, \\d{4})");
    private static final Pattern PERIOD = Pattern.compile("Bilt Blue Card\\s+([A-Z][a-z]{2} \\d{1,2})\\s+[^A-Za-z0-9]+\\s+([A-Z][a-z]{2} \\d{1,2}, \\d{4})");
    private static final Pattern FEES = Pattern.compile("Total fees charged in this period\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern INTEREST = Pattern.compile("Total interest for this period\\s+\\$([\\d,]+\\.\\d{2})");

    @Override public boolean supports(String text) { return text.contains("Bilt Blue Card") && text.contains("Cardless Inc."); }

    @Override public StatementSummary extract(String text) {
        Matcher balance = BALANCE.matcher(text); Matcher due = DUE.matcher(text); Matcher period = PERIOD.matcher(text);
        LocalDate cycleEnd = balance.find() ? LocalDate.parse(balance.group(1), DATE) : null;
        LocalDate cycleStart = null;
        if (period.find()) {
            cycleEnd = LocalDate.parse(period.group(2), DATE);
            cycleStart = LocalDate.parse(period.group(1) + ", " + cycleEnd.getYear(), DATE);
        }
        return new StatementSummary(balance.reset().find() ? amount(balance.group(2)) : null, amount(LIMIT, text), amount(AVAILABLE, text),
                amount(FEES, text), amount(INTEREST, text), amount(MINIMUM, text), due.find() ? LocalDate.parse(due.group(1), DATE) : null,
                cycleStart, cycleEnd);
    }

    private BigDecimal amount(Pattern pattern, String text) { Matcher match = pattern.matcher(text); return match.find() ? amount(match.group(1)) : null; }
    private BigDecimal amount(String value) { return new BigDecimal(value.replace(",", "")); }
}
