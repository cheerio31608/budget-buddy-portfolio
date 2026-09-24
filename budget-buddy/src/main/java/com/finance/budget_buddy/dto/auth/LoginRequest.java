package com.finance.budget_buddy.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email @jakarta.validation.constraints.Size(max = 254) String email,
        @NotBlank @jakarta.validation.constraints.Size(max = 72) String password
) {
}
