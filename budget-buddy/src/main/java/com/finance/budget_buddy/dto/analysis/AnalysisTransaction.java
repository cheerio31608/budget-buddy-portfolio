package com.finance.budget_buddy.dto.analysis;

import com.finance.budget_buddy.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Common input used by both database and CSV spending analysis. */
public record AnalysisTransaction(
        LocalDateTime transactionAt,
        String description,
        String vendorName,
        String categoryName,
        BigDecimal amount,
        TransactionType transactionType
) {
}
