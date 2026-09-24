package com.finance.budget_buddy.dto.transaction;

import com.finance.budget_buddy.dto.TransactionCreateRequest;
import com.finance.budget_buddy.entity.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** API input that deliberately excludes userId; the authenticated session owns the request. */
public record TransactionCreateApiRequest(
        @NotNull(message = "categoryId is required.")
        Long categoryId,

        @NotNull(message = "amount is required.")
        @Positive(message = "amount must be positive.")
        @Digits(integer = 13, fraction = 2, message = "amount must have at most 13 integer and 2 decimal digits.")
        BigDecimal amount,

        @NotNull(message = "transactionType is required.")
        TransactionType transactionType,

        @Size(max = 1000, message = "description must be 1000 characters or less.")
        String description,

        @NotNull(message = "transactionAt is required.")
        @PastOrPresent(message = "transactionAt must not be in the future.")
        LocalDateTime transactionAt,

        @Size(max = 255, message = "vendorName must be 255 characters or less.")
        String vendorName,

        @Size(max = 255, message = "location must be 255 characters or less.")
        String location,

        @Size(max = 100, message = "idempotencyKey must be 100 characters or less.")
        String idempotencyKey
) {
    public TransactionCreateRequest toCommand(Long authenticatedUserId) {
        return new TransactionCreateRequest(
                authenticatedUserId,
                categoryId,
                amount,
                transactionType,
                description,
                transactionAt,
                vendorName,
                location,
                idempotencyKey);
    }
}
