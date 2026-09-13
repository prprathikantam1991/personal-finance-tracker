package com.pradeep.finance.notification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.pradeep.finance.account.AccountOverviewResponse;
import com.pradeep.finance.account.AccountOverviewService;
import com.pradeep.finance.account.AccountType;
import org.springframework.stereotype.Service;

@Service
public class FinancialReminderService {
    private final AccountOverviewService accountOverviewService;

    public FinancialReminderService(AccountOverviewService accountOverviewService) {
        this.accountOverviewService = accountOverviewService;
    }

    public List<FinancialReminder> list() {
        LocalDate today = LocalDate.now();
        List<FinancialReminder> reminders = new ArrayList<>();
        for (AccountOverviewResponse account : accountOverviewService.list()) {
            if (account.nextExpectedStatementDate() != null) {
                LocalDate date = account.nextExpectedStatementDate();
                reminders.add(new FinancialReminder("statement-" + account.id(), "STATEMENT", account.id(), account.name(),
                        "Statement expected", "Expected around " + date + " based on the latest statement cycle.", date, severity(today, date)));
            }
            if (account.accountType() == AccountType.CREDIT_CARD && account.paymentDueDate() != null) {
                LocalDate date = nextMonthlyDate(account.paymentDueDate(), today);
                String minimum = account.minimumPayment() == null ? "Payment due" : "Minimum payment " + account.minimumPayment().setScale(2) + " due";
                reminders.add(new FinancialReminder("payment-" + account.id(), "PAYMENT_DUE", account.id(), account.name(),
                        minimum, "Projected from the most recent card statement.", date, severity(today, date)));
            }
            if (account.promotionalApr() != null && account.promotionalAprExpiresOn() != null) {
                LocalDate date = LocalDate.parse(account.promotionalAprExpiresOn());
                reminders.add(new FinancialReminder("promo-apr-" + account.id(), "PROMOTIONAL_APR", account.id(), account.name(),
                        "Promotional APR ends", account.promotionalApr() + "% promotional APR ends on this date.", date, severity(today, date)));
            }
        }
        return reminders.stream().sorted(Comparator.comparing(FinancialReminder::date).thenComparing(FinancialReminder::accountName)).toList();
    }

    private LocalDate nextMonthlyDate(LocalDate date, LocalDate today) {
        while (date.isBefore(today)) date = date.plusMonths(1);
        return date;
    }

    private String severity(LocalDate today, LocalDate date) {
        if (date.isBefore(today)) return "OVERDUE";
        if (!date.isAfter(today.plusDays(7))) return "URGENT";
        if (!date.isAfter(today.plusDays(30))) return "UPCOMING";
        return "PLANNED";
    }
}
