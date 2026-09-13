package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class WellsFargoCardSummaryExtractor implements StatementSummaryExtractor {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/uuuu");
    private static final Pattern PERIOD = Pattern.compile("Statement Period\\s+(\\d{2}/\\d{2}/\\d{4})\\s+to\\s+(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern DUE_DATE = Pattern.compile("Payment Due Date\\s+(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern MINIMUM = Pattern.compile("Minimum Payment\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern BALANCE = Pattern.compile("New Balance\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern LIMIT = Pattern.compile("Total Credit Limit\\s+\\$([\\d,]+(?:\\.\\d{2})?)");
    private static final Pattern AVAILABLE = Pattern.compile("Total Available Credit\\s+\\$([\\d,]+(?:\\.\\d{2})?)");
    private static final Pattern FEES = Pattern.compile("\\+ Fees Charged\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern INTEREST = Pattern.compile("\\+ Interest Charged\\s+\\$([\\d,]+\\.\\d{2})");

    @Override public boolean supports(String text) { return text.contains("WELLS FARGO") && text.contains("Total Credit Limit"); }
    @Override public StatementSummary extract(String text) {
        Matcher period = PERIOD.matcher(text);
        LocalDate cycleStart = null;
        LocalDate cycleEnd = null;
        if (period.find()) {
            cycleStart = LocalDate.parse(period.group(1), DATE);
            cycleEnd = LocalDate.parse(period.group(2), DATE);
        }
        return new StatementSummary(amount(BALANCE, text), amount(LIMIT, text), amount(AVAILABLE, text), amount(FEES, text), amount(INTEREST, text), amount(MINIMUM, text), date(DUE_DATE, text), cycleStart, cycleEnd);
    }
    private BigDecimal amount(Pattern pattern, String text) { Matcher match = pattern.matcher(text); return match.find() ? new BigDecimal(match.group(1).replace(",", "")) : null; }
    private LocalDate date(Pattern pattern, String text) { Matcher match = pattern.matcher(text); return match.find() ? LocalDate.parse(match.group(1), DATE) : null; }
}
