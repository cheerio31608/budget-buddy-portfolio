package com.finance.budget_buddy.service.csv;

import com.finance.budget_buddy.dto.analysis.AnalysisResult;
import com.finance.budget_buddy.dto.analysis.AnalysisTransaction;
import com.finance.budget_buddy.dto.csv.CsvAnalysisResponse;
import com.finance.budget_buddy.dto.csv.CsvColumnMapping;
import com.finance.budget_buddy.dto.csv.CsvPreviewResponse;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.service.AnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Coordinates CSV preview, validation and temporary analysis without persistence. */
@Service
public class CsvAnalysisService {

    private static final int PREVIEW_ROWS = 10;

    private final CsvParserService parserService;
    private final CsvMappingService mappingService;
    private final AnalysisService analysisService;

    public CsvAnalysisService(CsvParserService parserService,
                              CsvMappingService mappingService,
                              AnalysisService analysisService) {
        this.parserService = parserService;
        this.mappingService = mappingService;
        this.analysisService = analysisService;
    }

    public CsvPreviewResponse preview(MultipartFile file) {
        CsvTable table = parserService.parse(file);
        List<Map<String, String>> preview = table.rows().stream()
                .limit(PREVIEW_ROWS)
                .map(row -> toPreviewRow(table.headers(), row))
                .toList();
        return new CsvPreviewResponse(
                table.headers(),
                preview,
                mappingService.suggestMapping(table),
                table.rows().size());
    }

    public CsvAnalysisResponse analyze(MultipartFile file, CsvColumnMapping mapping) {
        CsvMappedRows mappedRows = mappingService.map(parserService.parse(file), mapping);
        if (mappedRows.transactions().isEmpty()) {
            return new CsvAnalysisResponse(mappedRows.validation(), null);
        }

        LocalDate start = mappedRows.transactions().stream()
                .map(AnalysisTransaction::transactionAt)
                .map(dateTime -> dateTime.toLocalDate())
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate end = mappedRows.transactions().stream()
                .map(AnalysisTransaction::transactionAt)
                .map(dateTime -> dateTime.toLocalDate())
                .max(LocalDate::compareTo)
                .orElseThrow();
        BigDecimal balance = mappedRows.transactions().stream()
                .map(transaction -> transaction.transactionType() == TransactionType.INCOME
                        ? transaction.amount()
                        : transaction.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AnalysisResult result = analysisService.analyze(mappedRows.transactions(), balance, start, end);
        return new CsvAnalysisResponse(mappedRows.validation(), result);
    }

    private Map<String, String> toPreviewRow(List<String> headers, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            row.put(headers.get(index), index < values.size() ? values.get(index) : "");
        }
        return row;
    }
}
