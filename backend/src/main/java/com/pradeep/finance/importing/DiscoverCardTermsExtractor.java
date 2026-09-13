package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pradeep.finance.account.ExtractedCardTerms;
import org.springframework.stereotype.Component;

@Component
public class DiscoverCardTermsExtractor implements CardTermsExtractor {
    private static final Pattern PURCHASE_PROMO = Pattern.compile("(?m)^Purchases\\s+(\\d+(?:\\.\\d+)?)%\\s+(\\d{2}/\\d{2}/\\d{2}).*$");
    private static final Pattern CREDIT_LINE = Pattern.compile("(?m)^Credit Line\\s+\\$(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?)");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/uu");

    @Override
    public boolean supports(String statementText) {
        return statementText.contains("DISCOVER IT CARD") && statementText.contains("Interest Charge Calculation");
    }

    @Override
    public ExtractedCardTerms extract(String statementText) {
        Matcher promotion = PURCHASE_PROMO.matcher(statementText);
        Matcher creditLine = CREDIT_LINE.matcher(statementText);
        BigDecimal promotionalApr = null;
        LocalDate promotionalExpiry = null;
        BigDecimal creditLimit = null;
        if (promotion.find()) {
            promotionalApr = new BigDecimal(promotion.group(1));
            promotionalExpiry = LocalDate.parse(promotion.group(2), DATE_FORMAT);
        }
        if (creditLine.find()) creditLimit = new BigDecimal(creditLine.group(1).replace(",", ""));
        return new ExtractedCardTerms(null, promotionalApr, promotionalExpiry, creditLimit, null);
    }
}
