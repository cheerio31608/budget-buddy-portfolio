package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.analysis.AnalysisResult;
import com.finance.budget_buddy.entity.Category;
import com.finance.budget_buddy.entity.Transaction;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.entity.User;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalysisQueryServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;

    private AnalysisQueryService analysisQueryService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        analysisQueryService = new AnalysisQueryService(
                transactionRepository,
                categoryRepository,
                userRepository,
                new AnalysisService(),
                clock);
    }

    @Test
    @DisplayName("Current month compares the same day range of the previous month and loads only required history")
    void getCurrentMonth_usesPreviousMonthToDateAndBoundedQuery() {
        User user = User.builder()
                .userId(1L)
                .email("user@test.com")
                .passwordHash("hash")
                .balance(new BigDecimal("100000.00"))
                .build();
        Category food = Category.builder()
                .categoryId(2L)
                .userId(1L)
                .name("Food")
                .type(TransactionType.EXPENSE)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(categoryRepository.findByUserIdOrderByNameAsc(1L)).willReturn(List.of(food));
        given(transactionRepository.findByUserIdAndTransactionAtGreaterThanEqualAndTransactionAtLessThanOrderByTransactionAtDesc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of(
                        transaction(2L, "2026-09-20T12:00:00", "70000"),
                        transaction(2L, "2026-09-10T12:00:00", "20000"),
                        transaction(2L, "2026-08-20T12:00:00", "50000"),
                        transaction(2L, "2026-08-10T12:00:00", "10000")));

        AnalysisResult result = analysisQueryService.getCurrentMonth(1L);

        assertThat(result.totalExpense()).isEqualByComparingTo("20000.00");
        assertThat(result.previousPeriodExpense()).isEqualByComparingTo("10000.00");
        assertThat(result.expenseChangeRate()).isEqualByComparingTo("100.00");
        verify(transactionRepository).findByUserIdAndTransactionAtGreaterThanEqualAndTransactionAtLessThanOrderByTransactionAtDesc(
                1L,
                LocalDateTime.parse("2026-04-01T00:00:00"),
                LocalDateTime.parse("2026-09-16T00:00:00"));
    }

    private Transaction transaction(Long categoryId, String transactionAt, String amount) {
        return Transaction.builder()
                .userId(1L)
                .categoryId(categoryId)
                .amount(new BigDecimal(amount))
                .balanceBefore(BigDecimal.ZERO)
                .balanceAfter(BigDecimal.ZERO)
                .transactionType(TransactionType.EXPENSE)
                .description("테스트 거래")
                .vendorName("테스트 상점")
                .transactionAt(LocalDateTime.parse(transactionAt))
                .build();
    }
}
