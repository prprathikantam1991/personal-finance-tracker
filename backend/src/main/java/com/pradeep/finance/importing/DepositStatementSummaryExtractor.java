package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DepositStatementSummaryExtractor implements StatementSummaryExtractor {
    private static final DateTimeFormatter MONTH_DATE = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US);
    private static final DateTimeFormatter NUMERIC_DATE = DateTimeFormatter.ofPattern("MM/dd/uuuu");
    private static final Pattern AMEX_NUMERIC_ENDING = Pattern.compile("Ending Balance as of (\\d{2}/\\d{2}/\\d{4})\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern AMEX_WORD_PERIOD = Pattern.compile("Statement Period:\\s+([A-Za-z]+ \\d{1,2}, \\d{4})\\s*-\\s*([A-Za-z]+ \\d{1,2}, \\d{4})");
    private static final Pattern AMEX_WORD_ENDING = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})\\s+Ending Balance\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern BOFA_PERIOD = Pattern.compile("for ([A-Za-z]+ \\d{1,2}, \\d{4}) to ([A-Za-z]+ \\d{1,2}, \\d{4})");
    private static final Pattern BOFA_ENDING = Pattern.compile("(?is)Ending\\s+balance\\s+on\\s+[A-Za-z]+\\s+\\d{1,2},\\s+\\d{4}\\s*\\$([\\d,]+\\.\\d{2})");
    private static final Pattern BOFA_BEGINNING = Pattern.compile("(?is)Beginning\\s+balance\\s+on\\s+[A-Za-z]+\\s+\\d{1,2},\\s+\\d{4}\\s*\\$([\\d,]+\\.\\d{2})");
    private static final Pattern BOFA_CREDITS = Pattern.compile("(?is)Deposits\\s+and\\s+other\\s+additions\\s*([\\d,]+\\.\\d{2})");
    private static final Pattern BOFA_DEBITS = Pattern.compile("(?is)Withdrawals\\s+and\\s+other\\s+subtractions\\s*-\\s*([\\d,]+\\.\\d{2})");
    private static final Pattern BOFA_SERVICE_FEES = Pattern.compile("(?is)Service\\s+fees\\s*-?\\s*\\$?([\\d,]+\\.\\d{2})");
    private static final Pattern WELLS_ENDING = Pattern.compile("(?is)Ending\\s+balance\\s+on\\s+\\d{1,2}/\\d{1,2}\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern WELLS_BEGINNING = Pattern.compile("(?is)Beginning\\s+balance\\s+on\\s+\\d{1,2}/\\d{1,2}\\s+\\$([\\d,]+\\.\\d{2})");
    private static final Pattern WELLS_CREDITS = Pattern.compile("(?is)Deposits/Additions\\s+([\\d,]+\\.\\d{2})");
    private static final Pattern WELLS_DEBITS = Pattern.compile("(?is)Withdrawals/Subtractions\\s+-\\s*([\\d,]+\\.\\d{2})");
    private static final Pattern WELLS_STATEMENT_DATE = Pattern.compile("(?is)Wells Fargo Everyday Checking\\s+([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})");
    private static final Pattern WELLS_PAGE_ONE_DATE = Pattern.compile("(?is)([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})\\s+Page\\s+1");
    private static final Pattern AMEX_BEGINNING = Pattern.compile("(?is)(?:Beginning Balance as of|Balance Last Statement)\\s+(?:\\d{2}/\\d{2}/\\d{4}\\s+)?\\$?([\\d,]+\\.\\d{2})");
    private static final Pattern AMEX_CREDITS = Pattern.compile("(?is)Total Credits This Period\\s+\\$?([\\d,]+\\.\\d{2})");
    private static final Pattern AMEX_DEBITS = Pattern.compile("(?is)Total Debits This Period\\s+-?\\$?([\\d,]+\\.\\d{2})");
    private static final Pattern AMEX_INTEREST_EARNED = Pattern.compile("(?is)Interest (?:Credited|Earned) This Period\\s+\\$?([\\d,]+\\.\\d{2})");
    private static final Pattern AMEX_RATE = Pattern.compile("(?is)(?:Annual )?Interest Rate\\*?\\s+([\\d.]+)%");
    private static final Pattern AMEX_APY = Pattern.compile("(?is)Annual Percentage Yield(?: Earned(?: This Period)?)?\\*?\\s+([\\d.]+)%");

    @Override public boolean supports(String text) {
        return text.contains("American Express National Bank") || text.contains("Your Adv Plus Banking") || text.contains("Wells Fargo Everyday Checking") || text.contains("Statement period activity summary");
    }

    @Override public StatementSummary extract(String text) {
        if (text.contains("American Express National Bank")) return amex(text);
        if (text.contains("Your Adv Plus Banking")) return bofa(text);
        return wells(text);
    }

    private StatementSummary amex(String text) {
        Matcher numeric = AMEX_NUMERIC_ENDING.matcher(text);
        if (numeric.find()) {
            LocalDate end = LocalDate.parse(numeric.group(1), NUMERIC_DATE);
            return depositSummary(amount(numeric.group(2)), null, end, text);
        }
        Matcher period = AMEX_WORD_PERIOD.matcher(text);
        Matcher ending = AMEX_WORD_ENDING.matcher(text);
        LocalDate start = null; LocalDate end = null; BigDecimal balance = null;
        if (period.find()) { start = LocalDate.parse(period.group(1), MONTH_DATE); end = LocalDate.parse(period.group(2), MONTH_DATE); }
        if (ending.find()) { end = LocalDate.parse(ending.group(1), NUMERIC_DATE); balance = amount(ending.group(2)); }
        return depositSummary(balance, start, end, text);
    }

    private StatementSummary bofa(String text) {
        Matcher period = BOFA_PERIOD.matcher(text); Matcher ending = BOFA_ENDING.matcher(text);
        LocalDate start = null; LocalDate end = null; BigDecimal balance = null;
        if (period.find()) { start = LocalDate.parse(period.group(1), MONTH_DATE); end = LocalDate.parse(period.group(2), MONTH_DATE); }
        if (ending.find()) balance = amount(ending.group(1));
        return new StatementSummary(balance, null, null, amount(BOFA_SERVICE_FEES, text), null, null, null, start, end,
                amount(BOFA_BEGINNING, text), amount(BOFA_CREDITS, text), amount(BOFA_DEBITS, text), null, null, null);
    }

    private StatementSummary wells(String text) {
        Matcher match = WELLS_ENDING.matcher(text);
        if (!match.find()) return StatementSummary.empty();
        Matcher statementDate = WELLS_STATEMENT_DATE.matcher(text);
        Matcher pageOneDate = WELLS_PAGE_ONE_DATE.matcher(text);
        LocalDate end = statementDate.find() ? LocalDate.parse(statementDate.group(1), MONTH_DATE)
                : pageOneDate.find() ? LocalDate.parse(pageOneDate.group(1), MONTH_DATE) : null;
        return new StatementSummary(amount(match.group(1)), null, null, null, null, null, null, null, end,
                amount(WELLS_BEGINNING, text), amount(WELLS_CREDITS, text), amount(WELLS_DEBITS, text), null, null, null);
    }

    private StatementSummary depositSummary(BigDecimal balance, LocalDate start, LocalDate end, String text) {
        return new StatementSummary(balance, null, null, null, null, null, null, start, end,
                amount(AMEX_BEGINNING, text), amount(AMEX_CREDITS, text), amount(AMEX_DEBITS, text),
                amount(AMEX_INTEREST_EARNED, text), amount(AMEX_RATE, text), amount(AMEX_APY, text));
    }

    private BigDecimal amount(String value) { return new BigDecimal(value.replace(",", "")); }
    private BigDecimal amount(Pattern pattern, String text) { Matcher match = pattern.matcher(text); return match.find() ? amount(match.group(1)) : null; }
}
