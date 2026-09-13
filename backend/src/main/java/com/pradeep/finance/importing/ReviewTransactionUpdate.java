package com.pradeep.finance.importing;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewTransactionUpdate(@NotNull LocalDate date, @NotBlank String description, @NotNull BigDecimal amount, BigDecimal balance) {}
