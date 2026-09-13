package com.pradeep.finance.importing;

import java.time.Instant;

public record ImportHistoryItem(
        String importId,
        String originalFilename,
        ImportSource source,
        ImportStatus status,
        String accountName,
        int transactionCount,
        Instant importedAt
) {}
