package com.finance.budget_buddy.repository;

import com.finance.budget_buddy.entity.Transaction;
import com.finance.budget_buddy.entity.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TransactionRangeRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Date range query excludes rows outside the analysis window and returns newest first")
    void findByUserIdAndTransactionAtRange_returnsOnlyRequestedRange() {
        save("2026-07-31T23:59:59", "before");
        save("2026-08-01T00:00:00", "start");
        save("2026-08-15T12:00:00", "middle");
        save("2026-09-01T00:00:00", "after");

        var result = transactionRepository
                .findByUserIdAndTransactionAtGreaterThanEqualAndTransactionAtLessThanOrderByTransactionAtDesc(
                1L,
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-09-01T00:00:00"));

        assertThat(result)
                .extracting(Transaction::getDescription)
                .containsExactly("middle", "start");
    }

    private void save(String transactionAt, String description) {
        transactionRepository.save(Transaction.builder()
                .userId(1L)
                .categoryId(2L)
                .amount(new BigDecimal("1000.00"))
                .balanceBefore(new BigDecimal("10000.00"))
                .balanceAfter(new BigDecimal("9000.00"))
                .transactionType(TransactionType.EXPENSE)
                .description(description)
                .transactionAt(LocalDateTime.parse(transactionAt))
                .idempotencyKey("range-" + description)
                .build());
    }
}
