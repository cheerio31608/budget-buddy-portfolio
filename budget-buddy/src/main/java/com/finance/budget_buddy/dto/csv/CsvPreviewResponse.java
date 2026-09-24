package com.finance.budget_buddy.dto.csv;

import java.util.List;
import java.util.Map;

public record CsvPreviewResponse(
        List<String> headers,
        List<Map<String, String>> rows,
        Map<String, String> suggestedMapping,
        int totalRows
) {
}
