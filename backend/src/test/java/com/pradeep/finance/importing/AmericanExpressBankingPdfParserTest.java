package com.pradeep.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AmericanExpressBankingPdfParserTest {
    private final AmericanExpressBankingPdfParser parser = new AmericanExpressBankingPdfParser();

    @Test
    void parsesSavingsCreditsAndDebits() {
        String statement = """
                American Express National Bank
                High Yield Savings Account xxxxxxxx7835
                Account Activity
                Date Transactions Debits Credits Balance
                08/04/2026 Beginning Balance $3,757.84
                08/04/2026 ACH Withdrawal
                AMEX EPAYMENT ACH PMT
                $49.31 $3,708.53
                08/14/2026 External ACH Deposit
                Express Internet transfer from WELLS FARGO
                $500.00 $4,208.53
                09/03/2026 Interest Payment $10.94 $4,219.47
                09/03/2026 Ending Balance $4,219.47
                00085520 footer
                """;

        List<ParsedTransaction> transactions = parser.parse(statement);

        assertThat(transactions).hasSize(3);
        assertThat(transactions).extracting(ParsedTransaction::amount)
                .extracting(Object::toString).containsExactly("-49.31", "500.00", "10.94");
    }

    @Test
    void parsesCheckingTransferDirections() {
        String statement = """
                American Express National Bank
                American Express Rewards Checking Statement
                Account Activity
                Date Description Credits Debits Balance
                08/14/2026 Online Transfer / Payment: Credit
                COGNIZANT TECHNO PAYROLL
                $500.00 $500.00
                08/14/2026 Internal Transfer Debit: Savings -7835
                -$500.00 $0.00
                08/31/2026 Ending Balance $0.00
                00085520 footer
                """;

        List<ParsedTransaction> transactions = parser.parse(statement);

        assertThat(transactions).extracting(ParsedTransaction::amount)
                .extracting(Object::toString).containsExactly("500.00", "-500.00");
    }
}
