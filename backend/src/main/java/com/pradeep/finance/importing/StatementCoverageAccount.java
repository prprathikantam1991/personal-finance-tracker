package com.pradeep.finance.importing;

import java.util.List;

/** Statement coverage derived from saved imports, not a prediction of a bank's statement schedule. */
public record StatementCoverageAccount(
        String accountId,
        String accountName,
        String institution,
        String accountType,
        String lastFour,
        List<StatementCoverageMonth> months
) {}
