package com.pradeep.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.pradeep.finance.account.ExtractedCardTerms;
import org.junit.jupiter.api.Test;

class DiscoverCardTermsExtractorTest {
    private final DiscoverCardTermsExtractor extractor = new DiscoverCardTermsExtractor();

    @Test
    void extractsPurchasePromotionAndCreditLine() {
        ExtractedCardTerms terms = extractor.extract("""
                DISCOVER IT CARD ENDING IN 3558
                Interest Charge Calculation
                Purchases 0.00% 07/06/27 $1,414.81 $0.00
                Cash Advances 28.49% V N/A $0.00 $0.00
                Credit Line $12,500
                """);

        assertThat(terms.promotionalApr()).isEqualByComparingTo("0.00");
        assertThat(terms.promotionalAprExpiresOn()).hasToString("2027-07-06");
        assertThat(terms.creditLimit()).isEqualByComparingTo("12500");
        assertThat(terms.currentApr()).isNull();
        assertThat(terms.penaltyApr()).isNull();
    }
}
