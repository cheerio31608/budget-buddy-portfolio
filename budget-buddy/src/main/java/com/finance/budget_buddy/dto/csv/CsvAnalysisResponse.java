package com.finance.budget_buddy.dto.csv;

import com.finance.budget_buddy.dto.analysis.AnalysisResult;

public record CsvAnalysisResponse(
        CsvValidationSummary validation,
        AnalysisResult analysis
) {
}
