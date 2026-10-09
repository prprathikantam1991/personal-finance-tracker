package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pradeep.finance.account.ExtractedCardTerms;
import org.springframework.stereotype.Component;

@Component
public class BiltCardTermsExtractor implements CardTermsExtractor {
    private static final Pattern PURCHASE_APR = Pattern.compile("Purchases\\s+(\\d+(?:\\.\\d+)?)% \\(variable\\)");
    private static final Pattern NEW_CARD_APR = Pattern.compile("New Card Purchases\\s+(\\d+(?:\\.\\d+)?)% \\(fixed\\)");
    private static final Pattern LIMIT = Pattern.compile("Credit limit\\s+\\$([\\d,]+\\.\\d{2})");

    @Override public boolean supports(String text) { return text.contains("Bilt Blue Card") && text.contains("Interest charge calculation"); }
    @Override public ExtractedCardTerms extract(String text) {
        return new ExtractedCardTerms(amount(PURCHASE_APR, text), amount(NEW_CARD_APR, text), null, amount(LIMIT, text), null);
    }
    private BigDecimal amount(Pattern pattern, String text) { Matcher match = pattern.matcher(text); return match.find() ? new BigDecimal(match.group(1).replace(",", "")) : null; }
}
