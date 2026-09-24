package com.finance.budget_buddy.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DatabaseConstraintTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Database rejects a balance snapshot that does not match the transaction amount")
    void snapshotEquation_rejectsInconsistentBalance() {
        assertThatThrownBy(() -> insertTransaction(1L, 2L, "EXPENSE", "1000.00", "10000.00", "9500.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database rejects a transaction type that differs from its category type")
    void categoryType_rejectsMismatch() {
        assertThatThrownBy(() -> insertTransaction(1L, 1L, "EXPENSE", "1000.00", "10000.00", "9000.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database rejects a category owned by another user")
    void categoryOwner_rejectsMismatch() {
        jdbcTemplate.update("""
                INSERT INTO users (user_id, email, password_hash, balance)
                VALUES (99, 'constraint-user@test.com', 'hash', 10000.00)
                """);
        jdbcTemplate.update("""
                INSERT INTO categories (category_id, user_id, name, type)
                VALUES (99, 99, 'Other user food', 'EXPENSE')
                """);

        assertThatThrownBy(() -> insertTransaction(1L, 99L, "EXPENSE", "1000.00", "10000.00", "9000.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertTransaction(Long userId,
                                   Long categoryId,
                                   String type,
                                   String amount,
                                   String balanceBefore,
                                   String balanceAfter) {
        jdbcTemplate.update("""
                        INSERT INTO transactions
                            (user_id, category_id, amount, balance_before, balance_after,
                             transaction_type, transaction_at, idempotency_key)
                        VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                        """,
                userId,
                categoryId,
                amount,
                balanceBefore,
                balanceAfter,
                type,
                "constraint-" + userId + "-" + categoryId + "-" + type + "-" + balanceAfter);
    }
}
