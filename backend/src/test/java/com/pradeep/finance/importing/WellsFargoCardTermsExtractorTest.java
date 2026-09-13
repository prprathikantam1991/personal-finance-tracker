package com.pradeep.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WellsFargoCardTermsExtractorTest {
    private final WellsFargoCardTermsExtractor extractor = new WellsFargoCardTermsExtractor();

    @Test
    void extractsPurchaseAprAndTotalCreditLimit() {
        var terms = extractor.extract("""
                WELLS FARGO AUTOGRAPH VISA SIGNATURE CARD
                Total Credit Limit $10,500.00
                PURCHASES 22.74% variable $0.00 30 $0.00 $107.62
                CASH ADVANCES 27.49% variable $0.00 30 $0.00 $0.00
                """);

        assertThat(terms.currentApr()).isEqualByComparingTo("22.74");
        assertThat(terms.creditLimit()).isEqualByComparingTo("10500.00");
    }
}
