package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.TransactionCreateRequest;
import com.finance.budget_buddy.entity.Category;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import com.finance.budget_buddy.repository.CategoryRepository;
import com.finance.budget_buddy.repository.TransactionRepository;
import com.finance.budget_buddy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Creates one reproducible portfolio scenario through the real transaction domain service. */
@Service
@Profile({"local", "h2", "compose"})
@RequiredArgsConstructor
public class DemoDataService {

    private static final int MAX_COUNT = 100;
    private static final BigDecimal OPENING_SALARY = new BigDecimal("3200000.00");
    private static final BigDecimal MONTHLY_SALARY = new BigDecimal("3100000.00");
    private static final List<ExpenseTemplate> EXPENSE_TEMPLATES = List.of(
            new ExpenseTemplate("Food", "커피하우스", "모닝 커피", "5500.00"),
            new ExpenseTemplate("Transport", "대중교통", "출퇴근 교통", "1500.00"),
            new ExpenseTemplate("Food", "한끼식당", "점심 식사", "12000.00"),
            new ExpenseTemplate("Food", "그린마켓", "장보기", "43800.00"),
            new ExpenseTemplate("Culture", "온라인서점", "도서 구입", "18500.00"),
            new ExpenseTemplate("Medical", "우리동네약국", "의약품", "17600.00"),
            new ExpenseTemplate("Transport", "택시", "늦은 귀가", "13200.00"),
            new ExpenseTemplate("Food", "커피하우스", "친구와 카페", "9800.00"),
            new ExpenseTemplate("Culture", "영화관", "문화생활", "15000.00"),
            new ExpenseTemplate("Housing", "월세·관리비", "주거비", "720000.00")
    );

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public DemoSeedResult createScenario(Long userId, int count) {
        if (count < 1 || count > MAX_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "데모 거래 건수는 1건 이상 100건 이하여야 합니다.");
        }

        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (transactionRepository.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.DEMO_DATA_ALREADY_EXISTS);
        }

        List<Category> categories = categoryRepository.findByUserIdOrderByNameAsc(userId);
        Category incomeCategory = categoryRepository
                .findFirstByUserIdAndTypeOrderByCategoryIdAsc(userId, TransactionType.INCOME)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        List<Category> expenseCategories = categories.stream()
                .filter(category -> category.getType() == TransactionType.EXPENSE)
                .toList();
        if (expenseCategories.isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        List<TransactionCreateRequest> requests = buildScenario(
                userId, count, now, incomeCategory, expenseCategories);
        requests.stream()
                .sorted(Comparator.comparing(TransactionCreateRequest::transactionAt))
                .forEach(transactionService::createTransaction);

        return new DemoSeedResult(count, requests.size());
    }

    private List<TransactionCreateRequest> buildScenario(Long userId,
                                                         int count,
                                                         LocalDateTime now,
                                                         Category incomeCategory,
                                                         List<Category> expenseCategories) {
        List<TransactionCreateRequest> requests = new ArrayList<>();
        requests.add(request(userId, incomeCategory, OPENING_SALARY,
                "세 달 전 급여", now.minusDays(91), "Budget Buddy 주식회사"));

        Map<String, Category> categoryByName = expenseCategories.stream()
                .collect(Collectors.toMap(
                        category -> category.getName().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left));
        Set<YearMonth> salaryMonths = new HashSet<>();
        int expenseIndex = 0;

        int generatedRows = count - 1;
        for (int index = 0; index < generatedRows; index++) {
            int daysAgo = generatedRows <= 1 ? 0 : (int) Math.round(index * 89.0 / (generatedRows - 1));
            LocalDateTime transactionAt = now.minusDays(daysAgo).minusHours(index % 8);
            YearMonth transactionMonth = YearMonth.from(transactionAt);

            if (salaryMonths.add(transactionMonth)) {
                requests.add(request(userId, incomeCategory, MONTHLY_SALARY,
                        transactionMonth + " 급여", transactionAt, "Budget Buddy 주식회사"));
                continue;
            }

            ExpenseTemplate template = EXPENSE_TEMPLATES.get(expenseIndex % EXPENSE_TEMPLATES.size());
            Category category = categoryByName.getOrDefault(
                    template.categoryName().toLowerCase(Locale.ROOT),
                    expenseCategories.get(expenseIndex % expenseCategories.size()));
            BigDecimal amount = template.amount().add(BigDecimal.valueOf((expenseIndex % 4) * 700L));
            requests.add(request(userId, category, amount, template.description(), transactionAt, template.vendorName()));
            expenseIndex++;
        }
        return requests;
    }

    private TransactionCreateRequest request(Long userId,
                                             Category category,
                                             BigDecimal amount,
                                             String description,
                                             LocalDateTime transactionAt,
                                             String vendorName) {
        return new TransactionCreateRequest(
                userId,
                category.getCategoryId(),
                amount,
                category.getType(),
                description,
                transactionAt,
                vendorName,
                null,
                null);
    }

    public record DemoSeedResult(int requestedCount, int createdCount) {
    }

    private record ExpenseTemplate(
            String categoryName,
            String vendorName,
            String description,
            BigDecimal amount
    ) {
        private ExpenseTemplate(String categoryName, String vendorName, String description, String amount) {
            this(categoryName, vendorName, description, new BigDecimal(amount));
        }
    }
}
