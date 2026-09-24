package com.finance.budget_buddy.service.csv;

import java.util.List;

record CsvTable(List<String> headers, List<List<String>> rows) {
}
