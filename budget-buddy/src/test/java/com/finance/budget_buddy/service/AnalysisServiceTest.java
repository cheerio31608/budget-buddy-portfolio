package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.analysis.AnalysisResult;
import com.finance.budget_buddy.dto.analysis.AnalysisTransaction;
import com.finance.budget_buddy.entity.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisServiceTest {

    private final AnalysisService analysisService = new AnalysisService();

    @Test
    @DisplayName("Period totals and previous-period expense change are calculated with BigDecimal")
    void analyze_calculatesPeriodAndComparison() {
        List<AnalysisTransaction> transactions = List.of(
                transaction("2026-08-10T09:00:00", "회사", "급여", "Salary", "3000000", TransactionType.INCOME),
                transaction("2026-08-12T12:00:00", "식당", "점심", "Food", "30000", TransactionType.EXPENSE),
                transaction("2026-08-15T18:00:00", "식당", "저녁", "Food", "70000", TransactionType.EXPENSE),
                transaction("2026-07-15T12:00:00", "버스", "교통", "Transport", "50000", TransactionType.EXPENSE)
        );

        AnalysisResult result = analysisService.analyze(
                transactions, new BigDecimal("2850000"),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result.totalIncome()).isEqualByComparingTo("3000000.00");
        assertThat(result.totalExpense()).isEqualByComparingTo("100000.00");
        assertThat(result.previousPeriodExpense()).isEqualByComparingTo("50000.00");
        assertThat(result.expenseChangeRate()).isEqualByComparingTo("100.00");
        assertThat(result.averageExpense()).isEqualByComparingTo("50000.00");
        assertThat(result.topCategory()).isEqualTo("Food");
        assertThat(result.topVendor()).isEqualTo("식당");
        assertThat(result.transactionCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Monthly and category aggregation keep zero months and sort categories by amount")
    void analyze_buildsTrendsAndCategoryOrder() {
        List<AnalysisTransaction> transactions = List.of(
                transaction("2026-08-01T10:00:00", "병원", "진료", "Medical", "150000", TransactionType.EXPENSE),
                transaction("2026-08-02T10:00:00", "마트", "장보기", "Food", "50000", TransactionType.EXPENSE),
                transaction("2026-07-02T10:00:00", "마트", "장보기", "Food", "40000", TransactionType.EXPENSE)
        );

        AnalysisResult result = analysisService.analyze(
                transactions, BigDecimal.ZERO,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result.categoryExpenses()).extracting(AnalysisResult.CategoryExpense::category)
                .containsExactly("Medical", "Food");
        assertThat(result.monthlyExpenses()).hasSize(6);
        assertThat(result.monthlyExpenses().get(4).amount()).isEqualByComparingTo("40000.00");
        assertThat(result.monthlyExpenses().get(5).amount()).isEqualByComparingTo("200000.00");
    }

    @Test
    @DisplayName("Explicit comparison period is used and transactions after period end are excluded")
    void analyze_usesComparisonPeriodAndCapsHistoricalResult() {
        List<AnalysisTransaction> transactions = List.of(
                transaction("2026-07-10T10:00:00", "마트", "전월 동기간", "Food", "10000", TransactionType.EXPENSE),
                transaction("2026-07-20T10:00:00", "마트", "전월 비교 제외", "Food", "80000", TransactionType.EXPENSE),
                transaction("2026-08-10T10:00:00", "마트", "분석 기간", "Food", "20000", TransactionType.EXPENSE),
                transaction("2026-08-20T10:00:00", "마트", "종료일 이후", "Food", "90000", TransactionType.EXPENSE)
        );

        AnalysisResult result = analysisService.analyze(
                transactions,
                BigDecimal.ZERO,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 15));

        assertThat(result.totalExpense()).isEqualByComparingTo("20000.00");
        assertThat(result.previousPeriodExpense()).isEqualByComparingTo("10000.00");
        assertThat(result.expenseChangeRate()).isEqualByComparingTo("100.00");
        assertThat(result.monthlyExpenses().get(5).amount()).isEqualByComparingTo("20000.00");
    }

    private AnalysisTransaction transaction(String date, String vendor, String description,
                                            String category, String amount, TransactionType type) {
        return new AnalysisTransaction(LocalDateTime.parse(date), description, vendor, category,
                new BigDecimal(amount), type);
    }
}
