package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.TransactionCreateRequest;
import com.finance.budget_buddy.entity.Category;
import com.finance.budget_buddy.entity.Transaction;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.entity.User;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import com.finance.budget_buddy.repository.CategoryRepository;
import com.finance.budget_buddy.repository.TransactionRepository;
import com.finance.budget_buddy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private TransactionService transactionService;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-05T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDateTime now = LocalDateTime.now(clock);

    private User user;
    private Category expenseCategory;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository, userRepository, categoryRepository, clock);
        user = User.builder()
                .userId(1L)
                .email("test@example.com")
                .passwordHash("hash")
                .balance(new BigDecimal("10000.00"))
                .build();

        expenseCategory = Category.builder()
                .categoryId(1L)
                .userId(1L)
                .name("Food")
                .type(TransactionType.EXPENSE)
                .build();
    }

    @Test
    @DisplayName("Expense transaction decreases balance and stores snapshots")
    void createTransaction_Success_Expense() {
        TransactionCreateRequest request = createRequest(1000L, TransactionType.EXPENSE);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(categoryRepository.findByCategoryIdAndUserId(1L, 1L)).willReturn(Optional.of(expenseCategory));
        given(transactionRepository.save(any(Transaction.class))).willAnswer(invocation -> invocation.getArgument(0));

        Transaction result = transactionService.createTransaction(request);

        assertThat(result.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.getBalanceBefore()).isEqualByComparingTo("10000.00");
        assertThat(result.getBalanceAfter()).isEqualByComparingTo("9000.00");
        assertThat(user.getBalance()).isEqualByComparingTo("9000.00");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Expense over current balance throws INSUFFICIENT_BALANCE")
    void createTransaction_Fail_InsufficientBalance() {
        TransactionCreateRequest request = createRequest(20000L, TransactionType.EXPENSE);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(categoryRepository.findByCategoryIdAndUserId(1L, 1L)).willReturn(Optional.of(expenseCategory));

        assertThatThrownBy(() -> transactionService.createTransaction(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("Future transaction date throws INVALID_INPUT_VALUE")
    void createTransaction_Fail_FutureDate() {
        TransactionCreateRequest request = createRequest(1000L, TransactionType.EXPENSE, now.plusDays(1));

        assertThatThrownBy(() -> transactionService.createTransaction(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("Category owned by another user is rejected")
    void createTransaction_Fail_CategoryOwnership() {
        TransactionCreateRequest request = createRequest(1000L, TransactionType.EXPENSE);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(categoryRepository.findByCategoryIdAndUserId(1L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("Amount with more than two decimal places is rejected before persistence")
    void createTransaction_Fail_AmountScale() {
        TransactionCreateRequest request = new TransactionCreateRequest(
                1L, 1L, new BigDecimal("1000.001"), TransactionType.EXPENSE,
                null, now, "amount-scale");

        assertThatThrownBy(() -> transactionService.createTransaction(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("소수 2자리");
    }

    @Test
    @DisplayName("Amount beyond DECIMAL(15,2) precision is rejected before persistence")
    void createTransaction_Fail_AmountPrecision() {
        TransactionCreateRequest request = new TransactionCreateRequest(
                1L, 1L, new BigDecimal("10000000000000.00"), TransactionType.EXPENSE,
                null, now, "amount-precision");

        assertThatThrownBy(() -> transactionService.createTransaction(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("정수 13자리");
    }

    @Test
    @DisplayName("Income that would overflow DECIMAL(15,2) balance is rejected before persistence")
    void createTransaction_Fail_BalancePrecision() {
        User nearLimitUser = User.builder()
                .userId(1L)
                .email("test@example.com")
                .passwordHash("hash")
                .balance(new BigDecimal("9999999999999.00"))
                .build();
        Category incomeCategory = Category.builder()
                .categoryId(1L)
                .userId(1L)
                .name("Salary")
                .type(TransactionType.INCOME)
                .build();
        TransactionCreateRequest request = createRequest(1L, TransactionType.INCOME);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(nearLimitUser));
        given(categoryRepository.findByCategoryIdAndUserId(1L, 1L)).willReturn(Optional.of(incomeCategory));

        assertThatThrownBy(() -> transactionService.createTransaction(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BALANCE_LIMIT_EXCEEDED);
        assertThat(nearLimitUser.getBalance()).isEqualByComparingTo("9999999999999.00");
    }

    @Test
    @DisplayName("Reusing an idempotency key with a different payload returns a conflict")
    void createTransaction_Fail_IdempotencyPayloadConflict() {
        TransactionCreateRequest request = new TransactionCreateRequest(
                1L, 1L, new BigDecimal("2000.00"), TransactionType.EXPENSE,
                null, now, "same-key");
        Transaction existing = Transaction.builder()
                .userId(1L)
                .categoryId(1L)
                .amount(new BigDecimal("1000.00"))
                .balanceBefore(new BigDecimal("10000.00"))
                .balanceAfter(new BigDecimal("9000.00"))
                .transactionType(TransactionType.EXPENSE)
                .transactionAt(now)
                .idempotencyKey("same-key")
                .build();
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(transactionRepository.findByUserIdAndIdempotencyKey(1L, "same-key"))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionService.createTransaction(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_CONFLICT);
        assertThat(user.getBalance()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("An identical idempotent retry returns the original transaction without another balance change")
    void createTransaction_IdempotentRetry_ReturnsOriginal() {
        TransactionCreateRequest request = new TransactionCreateRequest(
                1L, 1L, new BigDecimal("1000.00"), TransactionType.EXPENSE,
                null, now, "same-key");
        Transaction existing = Transaction.builder()
                .userId(1L)
                .categoryId(1L)
                .amount(new BigDecimal("1000.00"))
                .balanceBefore(new BigDecimal("10000.00"))
                .balanceAfter(new BigDecimal("9000.00"))
                .transactionType(TransactionType.EXPENSE)
                .transactionAt(now)
                .idempotencyKey("same-key")
                .build();
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(transactionRepository.findByUserIdAndIdempotencyKey(1L, "same-key"))
                .willReturn(Optional.of(existing));

        assertThat(transactionService.createTransaction(request)).isSameAs(existing);
        assertThat(user.getBalance()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("A backdated transaction stores snapshots in ledger posting order")
    void createTransaction_Backdated_UsesPostingBalance() {
        TransactionCreateRequest recentRequest = createRequest(
                1000L, TransactionType.EXPENSE, now.minusDays(1));
        TransactionCreateRequest backdatedRequest = createRequest(
                2000L, TransactionType.EXPENSE, now.minusDays(10));
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(categoryRepository.findByCategoryIdAndUserId(1L, 1L)).willReturn(Optional.of(expenseCategory));
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        transactionService.createTransaction(recentRequest);
        Transaction backdated = transactionService.createTransaction(backdatedRequest);

        assertThat(backdated.getTransactionAt()).isEqualTo(now.minusDays(10));
        assertThat(backdated.getBalanceBefore()).isEqualByComparingTo("9000.00");
        assertThat(backdated.getBalanceAfter()).isEqualByComparingTo("7000.00");
        assertThat(user.getBalance()).isEqualByComparingTo("7000.00");
    }

    private TransactionCreateRequest createRequest(long amount, TransactionType type) {
        return createRequest(amount, type, now);
    }

    private TransactionCreateRequest createRequest(long amount, TransactionType type, LocalDateTime time) {
        return new TransactionCreateRequest(
                1L,
                1L,
                new BigDecimal(amount),
                type,
                null,
                time,
                null);
    }

    @Test
    @DisplayName("Stored timestamps use microseconds so database rounding cannot break a retry")
    void createTransaction_normalizesTimestampBeforeSaving() {
        LocalDateTime preciseTime = now.minusSeconds(1).withNano(123456789);
        TransactionCreateRequest request = createRequest(1000L, TransactionType.EXPENSE, preciseTime);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(categoryRepository.findByCategoryIdAndUserId(1L, 1L)).willReturn(Optional.of(expenseCategory));
        given(transactionRepository.save(any(Transaction.class))).willAnswer(invocation -> invocation.getArgument(0));

        Transaction result = transactionService.createTransaction(request);

        assertThat(result.getTransactionAt()).isEqualTo(preciseTime.withNano(123456000));
    }
}
