package com.finance.budget_buddy.service.csv;

import com.finance.budget_buddy.dto.analysis.AnalysisTransaction;
import com.finance.budget_buddy.dto.csv.CsvValidationSummary;

import java.util.List;

record CsvMappedRows(List<AnalysisTransaction> transactions, CsvValidationSummary validation) {
}
