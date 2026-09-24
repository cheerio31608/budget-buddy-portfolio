package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.TransactionCreateRequest;
import com.finance.budget_buddy.entity.Transaction;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.entity.User;
import com.finance.budget_buddy.repository.TransactionRepository;
import com.finance.budget_buddy.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:concurrency;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class TransactionConcurrencyIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Concurrent expenses for one user keep balance and snapshots consistent")
    void concurrentExpenses_keepBalanceConsistent() throws Exception {
        transactionService.createTransaction(request(1L, new BigDecimal("10000.00"), TransactionType.INCOME, "initial-charge", "seed-1"));

        List<Callable<Transaction>> tasks = List.of(
                () -> transactionService.createTransaction(request(2L, new BigDecimal("3000.00"), TransactionType.EXPENSE, "expense-1", "expense-1")),
                () -> transactionService.createTransaction(request(2L, new BigDecimal("3000.00"), TransactionType.EXPENSE, "expense-2", "expense-2")),
                () -> transactionService.createTransaction(request(2L, new BigDecimal("3000.00"), TransactionType.EXPENSE, "expense-3", "expense-3"))
        );

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            for (var future : executor.invokeAll(tasks, 10, java.util.concurrent.TimeUnit.SECONDS)) {
                future.get(); // Fail the test if any worker fails or times out.
            }
        } finally {
            executor.shutdown();
        }

        User user = userRepository.findById(1L).orElseThrow();
        List<Transaction> expenses = transactionRepository.findByUserIdAndTransactionType(1L, TransactionType.EXPENSE)
                .stream()
                .sorted(Comparator.comparing(Transaction::getBalanceBefore).reversed())
                .toList();

        assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(expenses).hasSize(3);
        assertThat(expenses).extracting(Transaction::getBalanceBefore)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("10000.00"), new BigDecimal("7000.00"), new BigDecimal("4000.00"));
        assertThat(expenses).extracting(Transaction::getBalanceAfter)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("7000.00"), new BigDecimal("4000.00"), new BigDecimal("1000.00"));
    }

    private TransactionCreateRequest request(Long categoryId, BigDecimal amount, TransactionType type, String description, String idempotencyKey) {
        return new TransactionCreateRequest(
                1L,
                categoryId,
                amount,
                type,
                description,
                LocalDateTime.now(),
                idempotencyKey);
    }
}
