package com.pradeep.finance.importing;

import java.time.LocalDate;

/** One month in an account's statement-coverage timeline. */
public record StatementCoverageMonth(
        int month,
        CoverageStatus status,
        String importId,
        String originalFilename,
        LocalDate statementDate
) {
    public enum CoverageStatus { IMPORTED, MISSING, NOT_EXPECTED }
}
