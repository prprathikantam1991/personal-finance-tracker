package com.pradeep.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WellsFargoCardSummaryExtractorTest {
    private final WellsFargoCardSummaryExtractor extractor = new WellsFargoCardSummaryExtractor();

    @Test
    void extractsCardStatementSummary() {
        var summary = extractor.extract("""
                WELLS FARGO
                Statement Period 08/12/2026 to 09/10/2026
                Payment Due Date 10/05/2026
                Minimum Payment $25.00
                New Balance $107.62
                + Fees Charged $0.00
                + Interest Charged $0.00
                Total Credit Limit $10,500.00
                Total Available Credit $10,392.00
                """);

        assertThat(summary.statementBalance()).isEqualByComparingTo("107.62");
        assertThat(summary.creditLimit()).isEqualByComparingTo("10500.00");
        assertThat(summary.availableCredit()).isEqualByComparingTo("10392.00");
        assertThat(summary.minimumPayment()).isEqualByComparingTo("25.00");
        assertThat(summary.paymentDueDate()).hasToString("2026-10-05");
        assertThat(summary.cycleStartDate()).hasToString("2026-08-12");
        assertThat(summary.cycleEndDate()).hasToString("2026-09-10");
    }
}
