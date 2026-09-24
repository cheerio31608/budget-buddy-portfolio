package com.finance.budget_buddy.dto.csv;

public record CsvColumnMapping(
        String date,
        String description,
        String category,
        String amount,
        String type
) {
}
