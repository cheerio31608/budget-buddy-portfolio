package com.finance.budget_buddy.dto.csv;

public record CsvRowError(int rowNumber, String field, String message) {
}
