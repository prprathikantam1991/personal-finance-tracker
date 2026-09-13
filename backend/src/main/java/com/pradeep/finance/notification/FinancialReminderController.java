package com.pradeep.finance.notification;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminders")
public class FinancialReminderController {
    private final FinancialReminderService financialReminderService;

    public FinancialReminderController(FinancialReminderService financialReminderService) {
        this.financialReminderService = financialReminderService;
    }

    @GetMapping
    public List<FinancialReminder> list() { return financialReminderService.list(); }
}
