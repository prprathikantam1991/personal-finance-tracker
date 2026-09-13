package com.pradeep.finance.importing;

public interface StatementSummaryExtractor {
    boolean supports(String statementText);
    StatementSummary extract(String statementText);
}
