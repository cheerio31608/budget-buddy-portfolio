package com.finance.budget_buddy.dto.transaction;

import com.finance.budget_buddy.entity.Transaction;
import com.finance.budget_buddy.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Stable transaction API response without internal ownership or idempotency fields. */
public record TransactionResponse(
        Long transactionId,
        Long categoryId,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        TransactionType transactionType,
        String description,
        String vendorName,
        String location,
        LocalDateTime transactionAt,
        LocalDateTime createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getCategoryId(),
                transaction.getAmount(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getTransactionType(),
                transaction.getDescription(),
                transaction.getVendorName(),
                transaction.getLocation(),
                transaction.getTransactionAt(),
                transaction.getCreatedAt());
    }
}
