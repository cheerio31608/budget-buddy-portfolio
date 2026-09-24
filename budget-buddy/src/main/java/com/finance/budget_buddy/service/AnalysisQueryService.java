package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.analysis.AnalysisResult;
import com.finance.budget_buddy.dto.analysis.AnalysisTransaction;
import com.finance.budget_buddy.entity.Category;
import com.finance.budget_buddy.entity.Transaction;
import com.finance.budget_buddy.entity.User;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import com.finance.budget_buddy.repository.CategoryRepository;
import com.finance.budget_buddy.repository.TransactionRepository;
import com.finance.budget_buddy.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads persisted transactions, then delegates all calculations to AnalysisService. */
@Service
@Transactional(readOnly = true)
public class AnalysisQueryService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AnalysisService analysisService;
    private final Clock clock;

    public AnalysisQueryService(TransactionRepository transactionRepository,
                                CategoryRepository categoryRepository,
                                UserRepository userRepository,
                                AnalysisService analysisService,
                                Clock clock) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.analysisService = analysisService;
        this.clock = clock;
    }

    public AnalysisResult getCurrentMonth(Long userId) {
        LocalDate today = LocalDate.now(clock);
        YearMonth previousMonth = YearMonth.from(today).minusMonths(1);
        int comparableDay = Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth());
        return getAnalysis(
                userId,
                today.withDayOfMonth(1),
                today,
                previousMonth.atDay(1),
                previousMonth.atDay(comparableDay));
    }

    public AnalysisResult getMonth(Long userId, YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        if (month == null || month.getYear() < 1900 || month.isAfter(YearMonth.from(today))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "분석할 월을 확인해 주세요.");
        }
        if (month.equals(YearMonth.from(today))) return getCurrentMonth(userId);
        YearMonth previous = month.minusMonths(1);
        return getAnalysis(userId, month.atDay(1), month.atEndOfMonth(), previous.atDay(1), previous.atEndOfMonth());
    }

    public AnalysisResult getAnalysis(Long userId, LocalDate start, LocalDate end) {
        validatePeriod(start, end);
        long periodDays = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate comparisonEnd = start.minusDays(1);
        LocalDate comparisonStart = comparisonEnd.minusDays(periodDays - 1);
        return getAnalysis(userId, start, end, comparisonStart, comparisonEnd);
    }

    private AnalysisResult getAnalysis(Long userId,
                                       LocalDate start,
                                       LocalDate end,
                                       LocalDate comparisonStart,
                                       LocalDate comparisonEnd) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Map<Long, Category> categories = categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(Category::getCategoryId, Function.identity()));

        LocalDate earliestRequiredDate = analysisService.earliestRequiredDate(start, comparisonStart, end);
        List<AnalysisTransaction> analysisTransactions = transactionRepository
                .findByUserIdAndTransactionAtGreaterThanEqualAndTransactionAtLessThanOrderByTransactionAtDesc(
                        userId,
                        earliestRequiredDate.atStartOfDay(),
                        end.plusDays(1).atStartOfDay())
                .stream()
                .map(transaction -> toAnalysisTransaction(transaction, categories.get(transaction.getCategoryId())))
                .toList();

        return analysisService.analyze(
                analysisTransactions,
                user.getBalance(),
                start,
                end,
                comparisonStart,
                comparisonEnd);
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "분석 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private AnalysisTransaction toAnalysisTransaction(Transaction transaction, Category category) {
        return new AnalysisTransaction(
                transaction.getTransactionAt(),
                transaction.getDescription(),
                transaction.getVendorName(),
                category == null ? "미분류" : category.getName(),
                transaction.getAmount(),
                transaction.getTransactionType());
    }
}
