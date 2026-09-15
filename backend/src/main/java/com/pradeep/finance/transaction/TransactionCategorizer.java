package com.pradeep.finance.transaction;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TransactionCategorizer {
    private final CategoryRuleService categoryRuleService;
    public TransactionCategorizer(CategoryRuleService categoryRuleService) { this.categoryRuleService = categoryRuleService; }

    public String categorize(String description) {
        String savedCategory = categoryRuleService.categoryFor(description);
        if (savedCategory != null) return savedCategory;
        String value = description.toLowerCase(Locale.ROOT);
        if (value.contains("payroll")) return "Income";
        if (value.contains("transfer") || value.contains("zelle") || value.contains("e-payment") || value.contains("epayment") || value.contains("ccpymt") || value.contains("thank you") || value.contains("payment to crd") || value.contains("payment from chk") || value.contains("bank of america payment")) return "Transfer";
        if (value.contains("remit2any") || value.contains("cybrid-xpat")) return "India Remittance";
        if (value.contains("\"rent\"")) return "Rent";
        if (value.contains("bottle king")) return "Food & Drinks";
        if (value.contains("costco gas")) return "Gas & Fuel";
        if (value.contains("t-mobile") || value.contains("frontier online") || value.contains("pseg") || value.contains("public service des")) return "Utilities";
        if (value.contains("planet fitness")) return "Fitness";
        if (value.contains("nissan")) return "Auto & Transport";
        if (value.contains("patel") || value.contains("subzi mandi") || value.contains("walmart")) return "Groceries";
        if (value.contains("robinhood")) return "Investments";
        return "Uncategorized";
    }
}
