package com.finance.budget_buddy.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Server-calculated statistics consumed by the dashboard and Gemini prompt. */
public record AnalysisResult(
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal currentBalance,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal previousPeriodExpense,
        BigDecimal expenseChangeRate,
        BigDecimal averageExpense,
        long transactionCount,
        String topCategory,
        String topVendor,
        LargestExpense largestExpense,
        List<CategoryExpense> categoryExpenses,
        List<TimeSeriesPoint> monthlyExpenses,
        List<TimeSeriesPoint> weeklyExpenses,
        List<IncomeExpenseComparison> incomeExpenseComparison
) {
    public record CategoryExpense(String category, BigDecimal amount, long count) {
    }

    public record TimeSeriesPoint(String label, BigDecimal amount) {
    }

    public record IncomeExpenseComparison(String label, BigDecimal income, BigDecimal expense) {
    }

    public record LargestExpense(String description, String category, BigDecimal amount, LocalDate date) {
    }
}
