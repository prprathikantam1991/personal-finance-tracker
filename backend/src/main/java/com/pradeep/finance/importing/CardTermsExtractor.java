package com.pradeep.finance.importing;

import com.pradeep.finance.account.ExtractedCardTerms;

public interface CardTermsExtractor {
    boolean supports(String statementText);
    ExtractedCardTerms extract(String statementText);
}
