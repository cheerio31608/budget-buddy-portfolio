package com.finance.budget_buddy.dto.csv;

import java.util.List;

public record CsvValidationSummary(
        int totalRows,
        int validRows,
        int errorRows,
        int suspectedDuplicates,
        List<CsvRowError> errors
) {
}
