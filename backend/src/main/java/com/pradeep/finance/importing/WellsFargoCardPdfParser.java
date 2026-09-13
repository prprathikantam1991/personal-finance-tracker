package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WellsFargoCardPdfParser implements StatementTransactionParser {
    private static final Pattern ROW = Pattern.compile("^(?:\\d{4}\\s+)?(\\d{2}/\\d{2})\\s+\\d{2}/\\d{2}\\s+\\S+\\s+(.+?)\\s+(\\d{1,3}(?:,\\d{3})*\\.\\d{2})$");
    @Override public boolean supports(String text) { return text.contains("WELLS FARGO") && text.contains("Purchases, Balance Transfers & Other Charges") && text.contains("Transactions"); }
    @Override public String name() { return "Wells Fargo credit-card statement"; }
    @Override public List<ParsedTransaction> parse(String text) {
        List<ParsedTransaction> results = new ArrayList<>(); boolean payments = false; boolean purchases = false;
        for (String raw : text.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.equals("Payments")) { payments = true; purchases = false; continue; }
            if (line.equals("Purchases, Balance Transfers & Other Charges")) { purchases = true; payments = false; continue; }
            if (line.startsWith("TOTAL PAYMENTS") || line.startsWith("TOTAL PURCHASES")) { payments = false; purchases = false; continue; }
            if (!payments && !purchases) continue;
            Matcher row = ROW.matcher(line);
            if (row.matches()) {
                BigDecimal amount = new BigDecimal(row.group(3).replace(",", ""));
                if (payments) amount = amount.negate();
                results.add(new ParsedTransaction(LocalDate.parse(row.group(1) + "/" + Year.now().getValue(), DateTimeFormatter.ofPattern("MM/dd/uuuu")), row.group(2).replaceAll("\\s+", " ").trim(), amount, null));
            }
        }
        return results;
    }
}
