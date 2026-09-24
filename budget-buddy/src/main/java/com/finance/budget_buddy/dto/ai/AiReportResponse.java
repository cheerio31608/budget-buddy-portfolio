package com.finance.budget_buddy.dto.ai;

import com.finance.budget_buddy.entity.AiReport;

import java.time.LocalDateTime;

public record AiReportResponse(
        Long reportId,
        String reportType,
        String reportMonth,
        String reportContent,
        LocalDateTime generatedAt
) {
    public static AiReportResponse from(AiReport report) {
        return new AiReportResponse(
                report.getReportId(),
                report.getReportType(),
                report.getReportMonth(),
                report.getReportContent(),
                report.getGeneratedAt());
    }
}
