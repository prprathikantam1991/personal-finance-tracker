package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class AmericanExpressCardSummaryExtractor implements StatementSummaryExtractor {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/uu");
    private static final Pattern BALANCE = Pattern.compile("(?is)New Balance\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern MINIMUM = Pattern.compile("(?is)Minimum Payment Due\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern DUE_DATE = Pattern.compile("(?is)Payment Due Date\\s+(\\d{2}/\\d{2}/\\d{2})");
    private static final Pattern CLOSING_DATE = Pattern.compile("(?is)Closing Date\\s+(\\d{2}/\\d{2}/\\d{2})");
    private static final Pattern CREDIT = Pattern.compile("(?is)Credit Limit\\s+Available Credit\\s+\\$([\\d,]+\\.\\d{2})\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern FEES_AND_INTEREST = Pattern.compile("(?is)Account Summary\\s+Previous Balance\\s+Payments/Credits\\s+New Charges\\s+Fees\\s+Interest Charged\\s+\\$[\\d,]+\\.\\d{2}\\s+-\\$[\\d,]+\\.\\d{2}\\s+\\+\\$[\\d,]+\\.\\d{2}\\s+\\+\\$([\\d,]+\\.\\d{2})\\s+\\+\\$([\\d,]+\\.\\d{2})");

    @Override public boolean supports(String text) { return text.contains("American Express") && text.contains("New Charges"); }

    @Override public StatementSummary extract(String text) {
        Matcher credit = CREDIT.matcher(text);
        BigDecimal limit = null;
        BigDecimal available = null;
        BigDecimal fees = null;
        BigDecimal interest = null;
        if (credit.find()) {
            limit = amount(credit.group(1));
            available = amount(credit.group(2));
        }
        Matcher feesAndInterest = FEES_AND_INTEREST.matcher(text);
        if (feesAndInterest.find()) {
            fees = amount(feesAndInterest.group(1));
            interest = amount(feesAndInterest.group(2));
        }
        return new StatementSummary(amount(BALANCE, text), limit, available, fees, interest, amount(MINIMUM, text), date(DUE_DATE, text), null, date(CLOSING_DATE, text));
    }

    private BigDecimal amount(Pattern pattern, String text) { Matcher match = pattern.matcher(text); return match.find() ? amount(match.group(1)) : null; }
    private BigDecimal amount(String value) { return new BigDecimal(value.replace(",", "")); }
    private LocalDate date(Pattern pattern, String text) { Matcher match = pattern.matcher(text); return match.find() ? LocalDate.parse(match.group(1), DATE) : null; }
}
