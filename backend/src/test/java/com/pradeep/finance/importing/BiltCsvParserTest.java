package com.pradeep.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BiltCsvParserTest {
    private final BiltCsvParser parser = new BiltCsvParser();

    @Test
    void parsesBiltExportAndKeepsRawMerchant() {
        String export = """
                Transaction Date,Posted Date,Description,Amount,Card Last 4,Name on Card,Raw Merchant Name
                2026-04-15,2026-04-16,Uber,9.99,9484,Pradeep,UBER   *ONE MEMBERSHIP
                2026-04-03,2026-04-03,Bilt Rewards,1638.52,2658,Pradeep,BPS*BILT HOUSING
                2026-04-03,2026-04-03,Payment - Bilt Housing,-1638.52,,, 
                """;

        assertThat(parser.supports(export)).isTrue();
        assertThat(parser.parse(export)).extracting(ParsedTransaction::description)
                .containsExactly("UBER   *ONE MEMBERSHIP", "BPS*BILT HOUSING", "Payment - Bilt Housing");
        assertThat(parser.parse(export)).extracting(ParsedTransaction::amount)
                .extracting(Object::toString).containsExactly("9.99", "1638.52", "-1638.52");
    }
}
