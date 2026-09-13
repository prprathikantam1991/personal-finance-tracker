package com.pradeep.finance.importing;

import java.util.List;

import com.pradeep.finance.account.AccountIdentificationResponse;

public record StatementImportResponse(
        String importId,
        String originalFilename,
        ImportStatus status,
        AccountIdentificationResponse accountIdentification,
        List<ParsedTransaction> transactions,
        List<String> warnings
) {
}
