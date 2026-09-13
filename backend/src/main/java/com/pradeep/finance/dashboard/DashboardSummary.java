package com.pradeep.finance.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummary(BigDecimal income, BigDecimal expenses, BigDecimal indiaRemittance, BigDecimal netCashFlow, int reviewCount, List<CategoryTotal> categorySpending) {
    public record CategoryTotal(String category, BigDecimal amount) { }
}
