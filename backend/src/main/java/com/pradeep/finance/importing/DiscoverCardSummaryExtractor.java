package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class DiscoverCardSummaryExtractor implements StatementSummaryExtractor {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/uuuu");
    private static final Pattern PERIOD = Pattern.compile("(?s)(\\d{2}/\\d{2}/\\d{4})\\s*-\\s*(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern BALANCE = Pattern.compile("New Balance:\\s*\\$([\\d,]+\\.\\d{2})");
    private static final Pattern MINIMUM_AND_DUE = Pattern.compile("(?s)New Balance:\\s*\\$[\\d,]+\\.\\d{2}\\s+[\\d,]+\\.\\d{2}\\s+([\\d,]+\\.\\d{2})\\s+(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern CREDIT_LINE = Pattern.compile("Credit Line\\s+\\$([\\d,]+(?:\\.\\d{2})?)");
    private static final Pattern AVAILABLE_CREDIT = Pattern.compile("Credit Line Available\\s+\\$([\\d,]+(?:\\.\\d{2})?)");
    private static final Pattern FEES = Pattern.compile("Fees Charged\\s+\\+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern INTEREST = Pattern.compile("Interest Charged\\s+\\+\\$([\\d,]+\\.\\d{2})");

    @Override public boolean supports(String text) { return text.contains("DISCOVER IT CARD") && text.contains("Credit Line Available"); }

    @Override public StatementSummary extract(String text) {
        Matcher period = PERIOD.matcher(text);
        Matcher minimumAndDue = MINIMUM_AND_DUE.matcher(text);
        LocalDate cycleStart = null;
        LocalDate cycleEnd = null;
        if (period.find()) { cycleStart = LocalDate.parse(period.group(1), DATE); cycleEnd = LocalDate.parse(period.group(2), DATE); }
        BigDecimal minimum = null;
        LocalDate dueDate = null;
        if (minimumAndDue.find()) { minimum = amount(minimumAndDue.group(1)); dueDate = LocalDate.parse(minimumAndDue.group(2), DATE); }
        return new StatementSummary(amount(BALANCE, text), amount(CREDIT_LINE, text), amount(AVAILABLE_CREDIT, text), amount(FEES, text), amount(INTEREST, text), minimum, dueDate, cycleStart, cycleEnd);
    }

    private BigDecimal amount(Pattern pattern, String text) { Matcher match = pattern.matcher(text); return match.find() ? amount(match.group(1)) : null; }
    private BigDecimal amount(String value) { return new BigDecimal(value.replace(",", "")); }
}
