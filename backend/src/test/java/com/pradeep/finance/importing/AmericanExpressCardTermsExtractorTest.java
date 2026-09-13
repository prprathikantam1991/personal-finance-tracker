package com.pradeep.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AmericanExpressCardTermsExtractorTest {
    private final AmericanExpressCardTermsExtractor extractor = new AmericanExpressCardTermsExtractor();

    @Test
    void extractsPenaltyAprWithoutMislabelingItAsCurrentApr() {
        var terms = extractor.extract("""
                American Express New Charges Your APRs may be increased to the Penalty APR of 29.99%.
                Purchases 04/22/2026 28.49% (v) $0.00 $0.00
                Credit Limit $15,000.00
                """);

        assertThat(terms.penaltyApr()).isEqualByComparingTo("29.99");
        assertThat(terms.currentApr()).isEqualByComparingTo("28.49");
        assertThat(terms.creditLimit()).isEqualByComparingTo("15000.00");
        assertThat(terms.promotionalApr()).isNull();
    }
}
