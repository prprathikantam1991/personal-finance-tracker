package com.pradeep.finance.transaction;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record BulkCategoryUpdateRequest(
        @NotEmpty List<@NotBlank String> transactionIds,
        @NotBlank String category,
        boolean rememberForFuture) { }
