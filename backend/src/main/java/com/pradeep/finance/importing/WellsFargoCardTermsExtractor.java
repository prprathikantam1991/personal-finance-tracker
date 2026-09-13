package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pradeep.finance.account.ExtractedCardTerms;
import org.springframework.stereotype.Component;

@Component
public class WellsFargoCardTermsExtractor implements CardTermsExtractor {
    private static final Pattern PURCHASE_APR = Pattern.compile("(?im)^PURCHASES\\s+(\\d+(?:\\.\\d+)?)%\\s+(?:variable|fixed)");
    private static final Pattern CREDIT_LIMIT = Pattern.compile("(?im)^Total Credit Limit\\s+\\$([\\d,]+(?:\\.\\d{2})?)");

    @Override
    public boolean supports(String statementText) {
        return statementText.contains("WELLS FARGO") && statementText.contains("Total Credit Limit");
    }

    @Override
    public ExtractedCardTerms extract(String statementText) {
        Matcher purchaseApr = PURCHASE_APR.matcher(statementText);
        Matcher creditLimit = CREDIT_LIMIT.matcher(statementText);
        return new ExtractedCardTerms(
                purchaseApr.find() ? new BigDecimal(purchaseApr.group(1)) : null,
                null,
                null,
                creditLimit.find() ? new BigDecimal(creditLimit.group(1).replace(",", "")) : null,
                null
        );
    }
}
