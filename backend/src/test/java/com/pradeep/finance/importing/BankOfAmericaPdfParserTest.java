package com.pradeep.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class BankOfAmericaPdfParserTest {
    private final BankOfAmericaPdfParser parser = new BankOfAmericaPdfParser();

    @Test
    void parsesMultilineRowsAndIgnoresPageFooterText() {
        String statement = """
                Bank of America
                Deposits and other additions
                Date Description Amount
                07/22/26 Incoming payment 136.36
                Withdrawals and other subtractions
                Date Description Amount
                07/23/26 Long description that continues
                across a line
                -42.15
                Contacted unexpectedly by an individual claiming to be the bank
                Total withdrawals and other subtractions -$42.15
                """;

        List<ParsedTransaction> transactions = parser.parse(statement);

        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).amount()).isEqualByComparingTo(new BigDecimal("136.36"));
        assertThat(transactions.get(1).description()).isEqualTo("Long description that continues across a line");
        assertThat(transactions.get(1).amount()).isEqualByComparingTo(new BigDecimal("-42.15"));
    }

    @Test
    void stopsCollectingDescriptionAfterMarketingFooterBegins() {
        String statement = """
                Bank of America
                Deposits and other additions
                Date Description Amount
                Withdrawals and other subtractions
                Date Description Amount
                01/05/26 Mobile Banking payment to CRD 7098 Confirmation# 3znvdnno8 -500.00
                Help prevent check fraud Consider writing fewer checks.
                Instead, pay bills using our Mobile app or Online Banking.
                Mobile Banking requires that you download the Mobile Banking app.
                Total withdrawals and other subtractions -$500.00
                """;

        List<ParsedTransaction> transactions = parser.parse(statement);

        assertThat(transactions).singleElement().satisfies(transaction -> {
            assertThat(transaction.description()).isEqualTo("Mobile Banking payment to CRD 7098 Confirmation# 3znvdnno8");
            assertThat(transaction.amount()).isEqualByComparingTo("-500.00");
        });
    }
}
