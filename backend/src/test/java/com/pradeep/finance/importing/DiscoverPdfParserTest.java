package com.pradeep.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiscoverPdfParserTest {
    private final DiscoverPdfParser parser = new DiscoverPdfParser();

    @Test
    void parsesPaymentsAndWrappedPurchases() {
        String statement = """
                DISCOVER IT CARD
                PURCHASES MERCHANT CATEGORY
                ONLINE PHONE PAYMENTS
                08/07 INTERNET PAYMENT - THANK YOU -$130.00
                08/09 Sample merchant EAST WINDSOR
                NJ
                Merchandise $9.28
                PREVIOUS BALANCE $58.50
                """;
        List<ParsedTransaction> transactions = parser.parse(statement);
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).amount()).isEqualByComparingTo("-130.00");
        assertThat(transactions.get(1).description()).isEqualTo("Sample merchant EAST WINDSOR NJ Merchandise");
        assertThat(transactions.get(1).amount()).isEqualByComparingTo("9.28");
    }
}
