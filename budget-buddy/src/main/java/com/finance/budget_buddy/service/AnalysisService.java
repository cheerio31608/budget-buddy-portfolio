package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.analysis.AnalysisResult;
import com.finance.budget_buddy.dto.analysis.AnalysisTransaction;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Calculates deterministic statistics. AI is deliberately not involved here. */
@Service
public class AnalysisService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int TREND_MONTHS = 6;
    private static final int TREND_WEEKS = 8;

    public AnalysisResult analyze(List<AnalysisTransaction> allTransactions,
                                  BigDecimal currentBalance,
                                  LocalDate periodStart,
                                  LocalDate periodEnd) {
        validatePeriod(periodStart, periodEnd);

        long periodDays = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        LocalDate previousEnd = periodStart.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(periodDays - 1);
        return analyze(allTransactions, currentBalance, periodStart, periodEnd, previousStart, previousEnd);
    }

    /** Calculates statistics using an explicitly selected comparison period. */
    public AnalysisResult analyze(List<AnalysisTransaction> allTransactions,
                                  BigDecimal currentBalance,
                                  LocalDate periodStart,
                                  LocalDate periodEnd,
                                  LocalDate comparisonStart,
                                  LocalDate comparisonEnd) {
        validatePeriod(periodStart, periodEnd);
        validatePeriod(comparisonStart, comparisonEnd);

        // A historical report must never include transactions after its requested end date.
        List<AnalysisTransaction> transactionsThroughPeriodEnd = allTransactions.stream()
                .filter(transaction -> !transaction.transactionAt().toLocalDate().isAfter(periodEnd))
                .toList();

        List<AnalysisTransaction> periodTransactions = transactionsThroughPeriodEnd.stream()
                .filter(transaction -> isWithin(transaction.transactionAt().toLocalDate(), periodStart, periodEnd))
                .toList();

        BigDecimal totalIncome = sum(periodTransactions, TransactionType.INCOME);
        BigDecimal totalExpense = sum(periodTransactions, TransactionType.EXPENSE);
        long expenseCount = periodTransactions.stream()
                .filter(transaction -> transaction.transactionType() == TransactionType.EXPENSE)
                .count();
        BigDecimal averageExpense = expenseCount == 0
                ? BigDecimal.ZERO
                : totalExpense.divide(BigDecimal.valueOf(expenseCount), 2, RoundingMode.HALF_UP);

        BigDecimal previousExpense = transactionsThroughPeriodEnd.stream()
                .filter(transaction -> transaction.transactionType() == TransactionType.EXPENSE)
                .filter(transaction -> isWithin(transaction.transactionAt().toLocalDate(), comparisonStart, comparisonEnd))
                .map(AnalysisTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal changeRate = previousExpense.signum() == 0
                ? null
                : totalExpense.subtract(previousExpense)
                .divide(previousExpense, 4, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);

        List<AnalysisResult.CategoryExpense> categoryExpenses = categoryExpenses(periodTransactions);
        String topCategory = categoryExpenses.isEmpty() ? null : categoryExpenses.get(0).category();
        String topVendor = topVendor(periodTransactions);

        AnalysisResult.LargestExpense largestExpense = periodTransactions.stream()
                .filter(transaction -> transaction.transactionType() == TransactionType.EXPENSE)
                .max(Comparator.comparing(AnalysisTransaction::amount))
                .map(transaction -> new AnalysisResult.LargestExpense(
                        displayVendor(transaction),
                        transaction.categoryName(),
                        transaction.amount(),
                        transaction.transactionAt().toLocalDate()))
                .orElse(null);

        return new AnalysisResult(
                periodStart,
                periodEnd,
                scale(currentBalance),
                scale(totalIncome),
                scale(totalExpense),
                scale(previousExpense),
                changeRate,
                scale(averageExpense),
                periodTransactions.size(),
                topCategory,
                topVendor,
                largestExpense,
                categoryExpenses,
                monthlyExpenses(transactionsThroughPeriodEnd, periodEnd),
                weeklyExpenses(transactionsThroughPeriodEnd, periodEnd),
                incomeExpenseComparison(transactionsThroughPeriodEnd, periodEnd));
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "분석 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    LocalDate earliestRequiredDate(LocalDate periodStart, LocalDate comparisonStart, LocalDate periodEnd) {
        LocalDate monthlyTrendStart = YearMonth.from(periodEnd).minusMonths(TREND_MONTHS - 1L).atDay(1);
        LocalDate weeklyTrendStart = periodEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(TREND_WEEKS - 1L);
        return List.of(periodStart, comparisonStart, monthlyTrendStart, weeklyTrendStart).stream()
                .min(LocalDate::compareTo)
                .orElseThrow();
    }

    private List<AnalysisResult.CategoryExpense> categoryExpenses(List<AnalysisTransaction> transactions) {
        Map<String, List<AnalysisTransaction>> grouped = transactions.stream()
                .filter(transaction -> transaction.transactionType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        transaction -> defaultText(transaction.categoryName(), "미분류"),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> new AnalysisResult.CategoryExpense(
                        entry.getKey(),
                        scale(entry.getValue().stream().map(AnalysisTransaction::amount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)),
                        entry.getValue().size()))
                .sorted(Comparator.comparing(AnalysisResult.CategoryExpense::amount).reversed())
                .toList();
    }

    private String topVendor(List<AnalysisTransaction> transactions) {
        return transactions.stream()
                .filter(transaction -> transaction.transactionType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(this::displayVendor, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private List<AnalysisResult.TimeSeriesPoint> monthlyExpenses(List<AnalysisTransaction> transactions,
                                                                 LocalDate periodEnd) {
        YearMonth lastMonth = YearMonth.from(periodEnd);
        List<AnalysisResult.TimeSeriesPoint> points = new ArrayList<>();
        for (int index = TREND_MONTHS - 1; index >= 0; index--) {
            YearMonth month = lastMonth.minusMonths(index);
            BigDecimal amount = transactions.stream()
                    .filter(transaction -> transaction.transactionType() == TransactionType.EXPENSE)
                    .filter(transaction -> YearMonth.from(transaction.transactionAt()).equals(month))
                    .map(AnalysisTransaction::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(new AnalysisResult.TimeSeriesPoint(month.format(DateTimeFormatter.ofPattern("yy.MM")), scale(amount)));
        }
        return points;
    }

    private List<AnalysisResult.TimeSeriesPoint> weeklyExpenses(List<AnalysisTransaction> transactions,
                                                                LocalDate periodEnd) {
        LocalDate currentWeekStart = periodEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<AnalysisResult.TimeSeriesPoint> points = new ArrayList<>();
        for (int index = TREND_WEEKS - 1; index >= 0; index--) {
            LocalDate weekStart = currentWeekStart.minusWeeks(index);
            LocalDate weekEnd = weekStart.plusDays(6);
            BigDecimal amount = transactions.stream()
                    .filter(transaction -> transaction.transactionType() == TransactionType.EXPENSE)
                    .filter(transaction -> isWithin(transaction.transactionAt().toLocalDate(), weekStart, weekEnd))
                    .map(AnalysisTransaction::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(new AnalysisResult.TimeSeriesPoint(weekStart.format(DateTimeFormatter.ofPattern("M/d")), scale(amount)));
        }
        return points;
    }

    private List<AnalysisResult.IncomeExpenseComparison> incomeExpenseComparison(
            List<AnalysisTransaction> transactions, LocalDate periodEnd) {
        YearMonth lastMonth = YearMonth.from(periodEnd);
        List<AnalysisResult.IncomeExpenseComparison> points = new ArrayList<>();
        for (int index = TREND_MONTHS - 1; index >= 0; index--) {
            YearMonth month = lastMonth.minusMonths(index);
            List<AnalysisTransaction> monthTransactions = transactions.stream()
                    .filter(transaction -> YearMonth.from(transaction.transactionAt()).equals(month))
                    .toList();
            points.add(new AnalysisResult.IncomeExpenseComparison(
                    month.format(DateTimeFormatter.ofPattern("yy.MM")),
                    scale(sum(monthTransactions, TransactionType.INCOME)),
                    scale(sum(monthTransactions, TransactionType.EXPENSE))));
        }
        return points;
    }

    private BigDecimal sum(List<AnalysisTransaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(transaction -> transaction.transactionType() == type)
                .map(AnalysisTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isWithin(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private String displayVendor(AnalysisTransaction transaction) {
        if (transaction.vendorName() != null && !transaction.vendorName().isBlank()) {
            return transaction.vendorName().trim();
        }
        return defaultText(transaction.description(), "사용처 미입력");
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
