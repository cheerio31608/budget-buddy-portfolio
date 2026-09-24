package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.TransactionCreateRequest;
import com.finance.budget_buddy.entity.Category;
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
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoDataServiceTest {

    private static final Long USER_ID = 7L;
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private TransactionService transactionService;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;

    private DemoDataService demoDataService;

    @BeforeEach
    void setUp() {
        demoDataService = new DemoDataService(
                transactionService, transactionRepository, categoryRepository, userRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("Demo scenario is reproducible and uses the existing transaction service")
    void createScenario_createsDeterministicTransactions() {
        Category income = category(11L, "Salary", TransactionType.INCOME);
        Category food = category(12L, "Food", TransactionType.EXPENSE);
        Category transport = category(13L, "Transport", TransactionType.EXPENSE);
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user()));
        given(transactionRepository.existsByUserId(USER_ID)).willReturn(false);
        given(categoryRepository.findByUserIdOrderByNameAsc(USER_ID)).willReturn(List.of(income, food, transport));
        given(categoryRepository.findFirstByUserIdAndTypeOrderByCategoryIdAsc(USER_ID, TransactionType.INCOME))
                .willReturn(Optional.of(income));

        DemoDataService.DemoSeedResult result = demoDataService.createScenario(USER_ID, 3);

        ArgumentCaptor<TransactionCreateRequest> captor = ArgumentCaptor.forClass(TransactionCreateRequest.class);
        verify(transactionService, times(3)).createTransaction(captor.capture());
        List<TransactionCreateRequest> requests = captor.getAllValues();
        assertThat(result.requestedCount()).isEqualTo(3);
        assertThat(result.createdCount()).isEqualTo(3);
        assertThat(requests).extracting(TransactionCreateRequest::transactionAt).isSorted();
        assertThat(requests.get(0).amount()).isEqualByComparingTo(new BigDecimal("3200000.00"));
        assertThat(requests).allMatch(request -> request.userId().equals(USER_ID));
        assertThat(requests).allMatch(request -> !request.transactionAt()
                .isAfter(LocalDateTime.of(2026, 9, 15, 12, 0)));
    }

    @Test
    @DisplayName("Demo scenario can only be added to an empty account")
    void createScenario_rejectsSecondSeed() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user()));
        given(transactionRepository.existsByUserId(USER_ID)).willReturn(true);

        assertThatThrownBy(() -> demoDataService.createScenario(USER_ID, 30))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMO_DATA_ALREADY_EXISTS);
        verify(transactionService, never()).createTransaction(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Demo scenario rejects an out-of-range requested count")
    void createScenario_rejectsInvalidCount() {
        assertThatThrownBy(() -> demoDataService.createScenario(USER_ID, 101))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        verify(userRepository, never()).findByIdForUpdate(USER_ID);
    }

    private User user() {
        return User.builder()
                .userId(USER_ID)
                .email("demo@example.com")
                .passwordHash("hash")
                .balance(BigDecimal.ZERO)
                .build();
    }

    private Category category(Long id, String name, TransactionType type) {
        return Category.builder()
                .categoryId(id)
                .userId(USER_ID)
                .name(name)
                .type(type)
                .build();
    }
}
