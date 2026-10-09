package com.pradeep.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BiltCardSummaryExtractorTest {
    private final BiltCardSummaryExtractor extractor = new BiltCardSummaryExtractor();
    private final BiltCardTermsExtractor terms = new BiltCardTermsExtractor();

    @Test
    void extractsBiltBlueStatementSnapshotAndTerms() {
        String statement = """
                New balance as of Jul 6, 2026
                $0.00
                Minimum payment due
                $0.00
                Payment due by
                Aug 1, 2026
                Account summary
                Credit limit $10,000.00
                Available credit $10,000.00
                Bilt Blue Card
                Jun 7 � Jul 6, 2026
                Total fees charged in this period $0.00
                Total interest for this period $0.00
                Interest charge calculation
                Purchases 34.74% (variable) $0.00 $0.00
                New Card Purchases 10.00% (fixed) $0.00 $0.00
                Cardless Inc.
                """;
        StatementSummary summary = extractor.extract(statement);
        assertThat(summary.statementBalance()).isEqualByComparingTo("0.00");
        assertThat(summary.creditLimit()).isEqualByComparingTo("10000.00");
        assertThat(summary.paymentDueDate()).hasToString("2026-08-01");
        assertThat(summary.cycleEndDate()).hasToString("2026-07-06");
        assertThat(terms.extract(statement).currentApr()).isEqualByComparingTo("34.74");
        assertThat(terms.extract(statement).promotionalApr()).isEqualByComparingTo("10.00");
    }
}
