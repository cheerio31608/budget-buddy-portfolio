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
class RepositoryQueryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Category query returns only categories owned by the user")
    void findByCategoryIdAndUserId_returnsOwnedCategoryOnly() {
        assertThat(categoryRepository.findByCategoryIdAndUserId(2L, 1L)).isPresent();
        assertThat(categoryRepository.findByCategoryIdAndUserId(2L, 999L)).isEmpty();
    }

    @Test
    @DisplayName("Idempotency query finds duplicate transaction for the same user")
    void findByUserIdAndIdempotencyKey_findsSameUserDuplicate() {
        Transaction transaction = saveTransaction(1L, 2L, "idem-001");

        assertThat(transactionRepository.findByUserIdAndIdempotencyKey(1L, "idem-001"))
                .contains(transaction);
        assertThat(transactionRepository.findByUserIdAndIdempotencyKey(1L, "missing"))
                .isEmpty();
    }

    @Test
    @DisplayName("User transaction query orders the most recently posted transaction first")
    void findByUserIdOrderByTransactionIdDesc_ordersLatestPostingFirst() {
        saveTransaction(1L, 2L, "newer-event", LocalDateTime.now());
        Transaction latestPosting = saveTransaction(1L, 2L, "backdated-later", LocalDateTime.now().minusDays(2));

        assertThat(transactionRepository.findByUserIdOrderByTransactionIdDesc(1L))
                .first()
                .isEqualTo(latestPosting);
    }

    @Test
    @DisplayName("User categories are returned only for that user in name order")
    void findByUserIdOrderByNameAsc_returnsUserCategories() {
        assertThat(categoryRepository.findByUserIdOrderByNameAsc(1L))
                .isNotEmpty()
                .allMatch(category -> category.getUserId().equals(1L))
                .isSortedAccordingTo((left, right) -> left.getName().compareTo(right.getName()));
    }

    @Test
    @DisplayName("Demo data query finds the user's first income category")
    void findFirstByUserIdAndTypeOrderByCategoryIdAsc_returnsIncomeCategory() {
        assertThat(categoryRepository.findFirstByUserIdAndTypeOrderByCategoryIdAsc(1L, TransactionType.INCOME))
                .get()
                .extracting(category -> category.getCategoryId())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Transaction existence query is scoped to one user")
    void existsByUserId_checksOnlyRequestedUser() {
        saveTransaction(1L, 2L, "exists-user");

        assertThat(transactionRepository.existsByUserId(1L)).isTrue();
        assertThat(transactionRepository.existsByUserId(999L)).isFalse();
    }

    private Transaction saveTransaction(Long userId, Long categoryId, String idempotencyKey) {
        return saveTransaction(userId, categoryId, idempotencyKey, LocalDateTime.now());
    }

    private Transaction saveTransaction(Long userId, Long categoryId, String idempotencyKey, LocalDateTime transactionAt) {
        return transactionRepository.save(Transaction.builder()
                .userId(userId)
                .categoryId(categoryId)
                .amount(new BigDecimal("1000.00"))
                .balanceBefore(new BigDecimal("10000.00"))
                .balanceAfter(new BigDecimal("9000.00"))
                .transactionType(TransactionType.EXPENSE)
                .description("test")
                .transactionAt(transactionAt)
                .idempotencyKey(idempotencyKey)
                .build());
    }
}
