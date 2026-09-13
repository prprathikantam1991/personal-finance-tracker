package com.pradeep.finance.dashboard;

import java.math.BigDecimal;

public record MerchantSpending(String merchant, String category, BigDecimal amount, int transactionCount, BigDecimal previousAmount) {}
