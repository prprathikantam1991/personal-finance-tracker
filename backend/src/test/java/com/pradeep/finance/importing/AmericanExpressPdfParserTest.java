package com.pradeep.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AmericanExpressPdfParserTest {
    private final AmericanExpressPdfParser parser = new AmericanExpressPdfParser();

    @Test
    void parsesPaymentsCreditsAndMultilineCharges() {
        String statement = """
                American Express
                Payments Amount
                08/03/26* MOBILE PAYMENT - THANK YOU -$49.31
                Credits Amount
                09/02/26 Travel reservation credit
                -$10.84
                New Charges
                Detail
                08/09/26 Sample merchant
                EAST WINDSOR NJ
                $23.27
                Fees
                """;
        List<ParsedTransaction> transactions = parser.parse(statement);
        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).amount()).isEqualByComparingTo("-49.31");
        assertThat(transactions.get(1).amount()).isEqualByComparingTo("-10.84");
        assertThat(transactions.get(2).description()).isEqualTo("Sample merchant EAST WINDSOR NJ");
    }
}
