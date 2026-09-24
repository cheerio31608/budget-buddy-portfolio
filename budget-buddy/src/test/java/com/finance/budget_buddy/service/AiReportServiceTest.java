package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.analysis.AnalysisResult;
import com.finance.budget_buddy.entity.AiReport;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import com.finance.budget_buddy.repository.AiReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class AiReportServiceTest {

    @Mock
    private GeminiClient geminiClient;
    @Mock
    private AnalysisQueryService analysisQueryService;
    @Mock
    private AiReportRepository aiReportRepository;
    @Mock
    private AiQuotaService quota;

    private AiReportService aiReportService;

    @BeforeEach
    void setUp() {
        aiReportService = new AiReportService(geminiClient, analysisQueryService, aiReportRepository, quota);
    }

    @Test
    @DisplayName("Monthly report sends only calculated statistics to Gemini and stores the response")
    void generateMonthlyReport_usesAggregateAndPersists() {
        AnalysisResult analysis = analysis(4);
        given(analysisQueryService.getCurrentMonth(1L)).willReturn(analysis);
        given(geminiClient.generateContent(org.mockito.ArgumentMatchers.anyString())).willReturn("분석 결과");

        String result = aiReportService.generateMonthlyReport(1L);

        assertThat(result).isEqualTo("분석 결과");
        var callOrder = inOrder(geminiClient, quota);
        callOrder.verify(geminiClient).ensureConfigured();
        callOrder.verify(quota).reserve(1L);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateContent(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("총지출] 400000.00원", "카테고리별 지출", "Food: 300000.00원")
                .doesNotContain("transactionId", "balanceBefore");
        ArgumentCaptor<AiReport> reportCaptor = ArgumentCaptor.forClass(AiReport.class);
        verify(aiReportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getReportType()).isEqualTo("MONTHLY");
        assertThat(reportCaptor.getValue().getReportContent()).isEqualTo("분석 결과");
    }

    @Test
    @DisplayName("Missing Gemini configuration is rejected without consuming AI quota")
    void generateMonthlyReport_doesNotReserveQuotaWhenGeminiIsNotConfigured() {
        given(analysisQueryService.getCurrentMonth(1L)).willReturn(analysis(4));
        org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.AI_NOT_CONFIGURED))
                .given(geminiClient).ensureConfigured();

        assertThatThrownBy(() -> aiReportService.generateMonthlyReport(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_NOT_CONFIGURED);

        verify(quota, never()).reserve(1L);
        verify(geminiClient, never()).generateContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("AI report is not requested when there is no analyzable transaction")
    void generateCsvReport_rejectsEmptyAnalysis() {
        assertThatThrownBy(() -> aiReportService.generateCsvReport(1L, analysis(0)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ANALYSIS_DATA_NOT_FOUND);
    }

    @Test
    @DisplayName("CSV report describes the selected analysis period instead of calling it this month")
    void generateCsvReport_usesPeriodHeading() {
        given(geminiClient.generateContent(org.mockito.ArgumentMatchers.anyString())).willReturn("CSV 분석 결과");

        aiReportService.generateCsvReport(1L, analysis(4));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateContent(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("### 분석 기간 소비 요약")
                .doesNotContain("### 이번 달 소비 요약");
    }

    private AnalysisResult analysis(long count) {
        return new AnalysisResult(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("2600000.00"), new BigDecimal("3000000.00"),
                new BigDecimal("400000.00"), new BigDecimal("500000.00"),
                new BigDecimal("-20.00"), new BigDecimal("100000.00"), count,
                count == 0 ? null : "Food", count == 0 ? null : "동네마트", null,
                count == 0 ? List.of() : List.of(new AnalysisResult.CategoryExpense("Food", new BigDecimal("300000.00"), 3)),
                List.of(), List.of(), List.of());
    }
}
