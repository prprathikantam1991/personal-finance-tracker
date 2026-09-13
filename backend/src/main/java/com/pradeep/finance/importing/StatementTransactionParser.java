package com.pradeep.finance.importing;

import java.util.List;

public interface StatementTransactionParser {
    boolean supports(String statementText);
    List<ParsedTransaction> parse(String statementText);
    String name();
}
