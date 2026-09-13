package com.pradeep.finance.dashboard;

import java.math.BigDecimal;

public record MonthlyTrend(
        String month,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal indiaRemittance,
        BigDecimal netCashFlow
) { }
