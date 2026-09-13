package com.pradeep.finance.notification;

import java.time.LocalDate;

public record FinancialReminder(
        String id,
        String type,
        String accountId,
        String accountName,
        String title,
        String detail,
        LocalDate date,
        String severity
) {}
