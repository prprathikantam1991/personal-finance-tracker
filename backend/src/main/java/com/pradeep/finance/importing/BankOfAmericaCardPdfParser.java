package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BankOfAmericaCardPdfParser implements StatementTransactionParser {
    private static final Pattern PAYMENT = Pattern.compile("(?s)Payments and Other Credits\\s+(\\d{2}/\\d{2})\\s+\\d{2}/\\d{2}\\s+(.+?)\\s+\\d{4}\\s+\\d{4}\\s+(-?[\\d,]+\\.\\d{2})");
    @Override public String name() { return "Bank of America credit card PDF"; }
    @Override public boolean supports(String text) { return text.contains("Visa Signature") && text.contains("Payments and Other Credits"); }
    @Override public List<ParsedTransaction> parse(String text) {
        Matcher match = PAYMENT.matcher(text);
        if (!match.find()) return List.of();
        LocalDate date = LocalDate.parse(match.group(1) + "/2026", DateTimeFormatter.ofPattern("MM/dd/uuuu"));
        return List.of(new ParsedTransaction(date, match.group(2).replaceAll("\\s+", " ").trim(), new BigDecimal(match.group(3).replace(",", "")), null));
    }
}
