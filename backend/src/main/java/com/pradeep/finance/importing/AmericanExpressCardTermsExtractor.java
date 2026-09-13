package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pradeep.finance.account.ExtractedCardTerms;
import org.springframework.stereotype.Component;

@Component
public class AmericanExpressCardTermsExtractor implements CardTermsExtractor {
    private static final Pattern PENALTY_APR = Pattern.compile("(?i)Penalty APR(?: of)?\\s+(\\d+(?:\\.\\d+)?)%");
    private static final Pattern PURCHASE_APR = Pattern.compile("(?im)^Purchases\\s+\\d{2}/\\d{2}/\\d{4}\\s+(\\d+(?:\\.\\d+)?)%");
    private static final Pattern CREDIT_LIMIT = Pattern.compile("(?is)Credit Limit\\s+(?:Available Credit\\s+)?\\$([\\d,]+\\.\\d{2})");

    @Override
    public boolean supports(String statementText) {
        return statementText.contains("American Express") && statementText.contains("New Charges");
    }

    @Override
    public ExtractedCardTerms extract(String statementText) {
        Matcher penaltyApr = PENALTY_APR.matcher(statementText);
        Matcher purchaseApr = PURCHASE_APR.matcher(statementText);
        Matcher creditLimit = CREDIT_LIMIT.matcher(statementText);
        return new ExtractedCardTerms(purchaseApr.find() ? new BigDecimal(purchaseApr.group(1)) : null, null, null,
                creditLimit.find() ? new BigDecimal(creditLimit.group(1).replace(",", "")) : null,
                penaltyApr.find() ? new BigDecimal(penaltyApr.group(1)) : null);
    }
}
