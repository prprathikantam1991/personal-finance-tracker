package com.pradeep.finance.transaction;

import jakarta.validation.constraints.NotBlank;

public record CategoryUpdateRequest(@NotBlank String category, boolean rememberForFuture) { }
