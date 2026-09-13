package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReviewTransaction(String id, LocalDate date, String description, BigDecimal amount, BigDecimal balance, ImportStatus status) {}
