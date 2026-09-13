package com.pradeep.finance.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountIdentificationRequest(
        @NotBlank @Size(max = 120) String institution,
        @NotNull AccountType accountType,
        @Pattern(regexp = "\\d{4}", message = "lastFour must contain exactly four digits") String lastFour,
        @Size(max = 120) String suggestedName,
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a three-letter ISO code") String currency
) {
}

