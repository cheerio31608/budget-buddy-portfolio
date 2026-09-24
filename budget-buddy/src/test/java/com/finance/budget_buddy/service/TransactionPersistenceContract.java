package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.TransactionCreateRequest;
import com.finance.budget_buddy.entity.Transaction;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import com.finance.budget_buddy.repository.TransactionRepository;
import com.finance.budget_buddy.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** The same committed-state checks run against both H2 and PostgreSQL. */
abstract class TransactionPersistenceContract {
    protected static final long USER_ID = 10001L;
    protected static final long INCOME_ID = 10001L;
    protected static final long EXPENSE_ID = 10002L;
    protected static final LocalDateTime EVENT_TIME = LocalDateTime.of(2020, 1, 1, 12, 0);

    @Autowired protected TransactionService service;
    @Autowired protected TransactionRepository transactions;
    @Autowired protected UserRepository users;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void createIndependentFixture() {
        removeFixture();
        jdbc.update("INSERT INTO users (user_id, email, password_hash, balance) VALUES (?, ?, ?, ?)",
                USER_ID, "contract@test.invalid", "unused-test-hash", new BigDecimal("10000.00"));
        jdbc.update("INSERT INTO categories (category_id, user_id, name, type) VALUES (?, ?, ?, ?)",
                INCOME_ID, USER_ID, "Salary", "INCOME");
        jdbc.update("INSERT INTO categories (category_id, user_id, name, type) VALUES (?, ?, ?, ?)",
                EXPENSE_ID, USER_ID, "Food", "EXPENSE");
    }

    @AfterEach
    void removeFixture() {
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM categories WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM users WHERE user_id = ?", USER_ID);
    }

    @Test
    void committedExpense_updatesBalanceAndSnapshots() {
        Transaction saved = service.createTransaction(expense("3000.00", "expense"));
        Transaction reloaded = transactions.findById(saved.getTransactionId()).orElseThrow();
        assertThat(reloaded.getBalanceBefore()).isEqualByComparingTo("10000.00");
        assertThat(reloaded.getBalanceAfter()).isEqualByComparingTo("7000.00");
        assertBalanceAndCount("7000.00", 1);
    }

    @Test
    void exceptionAfterFlush_rollsBackBothBalanceAndTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.createTransaction(expense("3000.00", "rollback"));
            entityManager.flush();
            throw new IllegalStateException("forced failure after both SQL writes");
        })).isInstanceOf(IllegalStateException.class);

        assertBalanceAndCount("10000.00", 0);
    }

    @Test
    void databaseFailureAfterFlush_rollsBackTheWholeUnitOfWork() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Transaction saved = service.createTransaction(expense("3000.00", "database-rollback"));
            entityManager.flush();
            // Deliberately violate the snapshot CHECK after valid writes reached the DB.
            jdbc.update("UPDATE transactions SET balance_after = -1 WHERE transaction_id = ?", saved.getTransactionId());
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertBalanceAndCount("10000.00", 0);
    }

    @Test
    void retryWithNanoseconds_returnsOriginalWithoutAnotherDebit() {
        TransactionCreateRequest request = new TransactionCreateRequest(USER_ID, EXPENSE_ID,
                new BigDecimal("1000.00"), TransactionType.EXPENSE, "retry",
                EVENT_TIME.withNano(123456789), "retry-key");
        Transaction original = service.createTransaction(request);
        Transaction retry = service.createTransaction(request);
        assertThat(retry.getTransactionId()).isEqualTo(original.getTransactionId());
        assertBalanceAndCount("9000.00", 1);
    }

    @Test
    void sameKeyWithDifferentAmount_isRejectedWithoutChangingBalance() {
        service.createTransaction(expense("1000.00", "conflict"));
        assertThatThrownBy(() -> service.createTransaction(expense("2000.00", "conflict")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_CONFLICT);
        assertBalanceAndCount("9000.00", 1);
    }

    @Test
    void uniqueConstraint_blocksDuplicateEvenWhenServiceIsBypassed() {
        service.createTransaction(expense("1000.00", "unique"));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (user_id, category_id, amount, balance_before, balance_after,
                    transaction_type, transaction_at, idempotency_key)
                SELECT user_id, category_id, amount, balance_before, balance_after,
                    transaction_type, transaction_at, idempotency_key FROM transactions WHERE user_id = ?
                """, USER_ID)).isInstanceOf(DataIntegrityViolationException.class);
        assertBalanceAndCount("9000.00", 1);
    }

    @RepeatedTest(3)
    void concurrentOverspending_onlyOneExpenseSucceeds() throws Exception {
        List<Boolean> results = concurrently(List.of(
                () -> tryExpense("expense-a"), () -> tryExpense("expense-b")));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertBalanceAndCount("3000.00", 1);
        Transaction saved = transactions.findByUserIdOrderByTransactionIdDesc(USER_ID).get(0);
        assertThat(saved.getBalanceBefore()).isEqualByComparingTo("10000.00");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo("3000.00");
    }

    @RepeatedTest(3)
    void concurrentIdenticalRetries_createExactlyOneTransaction() throws Exception {
        TransactionCreateRequest request = expense("1000.00", "concurrent-retry");
        List<Callable<Long>> calls = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            calls.add(() -> service.createTransaction(request).getTransactionId());
        }
        assertThat(concurrently(calls)).containsOnly(
                transactions.findByUserIdAndIdempotencyKey(USER_ID, "concurrent-retry").orElseThrow().getTransactionId());
        assertBalanceAndCount("9000.00", 1);
    }

    private boolean tryExpense(String key) {
        try {
            service.createTransaction(expense("7000.00", key));
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
            return false;
        }
    }

    protected TransactionCreateRequest expense(String amount, String key) {
        return new TransactionCreateRequest(USER_ID, EXPENSE_ID, new BigDecimal(amount),
                TransactionType.EXPENSE, "contract expense", EVENT_TIME, key);
    }

    protected void assertBalanceAndCount(String balance, int count) {
        assertThat(users.findById(USER_ID).orElseThrow().getBalance()).isEqualByComparingTo(balance);
        assertThat(transactions.findByUserIdOrderByTransactionIdDesc(USER_ID)).hasSize(count);
    }

    private <T> List<T> concurrently(List<Callable<T>> calls) throws Exception {
        var executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (Callable<T> call : calls) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                    return call.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(15, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
