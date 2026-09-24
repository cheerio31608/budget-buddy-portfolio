package com.finance.budget_buddy.controller;

import com.finance.budget_buddy.dto.csv.CsvAnalysisResponse;
import com.finance.budget_buddy.dto.csv.CsvColumnMapping;
import com.finance.budget_buddy.dto.csv.CsvPreviewResponse;
import com.finance.budget_buddy.security.BudgetBuddyPrincipal;
import com.finance.budget_buddy.service.AiReportService;
import com.finance.budget_buddy.service.csv.CsvAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/csv")
public class CsvAnalysisController {

    private final CsvAnalysisService csvAnalysisService;
    private final AiReportService aiReportService;
    private final com.finance.budget_buddy.service.csv.CsvImportService importer;

    public CsvAnalysisController(CsvAnalysisService csvAnalysisService, AiReportService aiReportService,
            com.finance.budget_buddy.service.csv.CsvImportService importer) {
        this.csvAnalysisService = csvAnalysisService;
        this.aiReportService = aiReportService;
        this.importer = importer;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public com.finance.budget_buddy.service.csv.CsvImportService.ImportResult importFile(
            @AuthenticationPrincipal BudgetBuddyPrincipal principal,
            @RequestPart("file") MultipartFile file, @RequestPart("mapping") CsvColumnMapping mapping) {
        return importer.importFile(principal.userId(), file, mapping);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvPreviewResponse> preview(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(csvAnalysisService.preview(file));
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvAnalysisResponse> analyze(
            @RequestPart("file") MultipartFile file,
            @RequestPart("mapping") CsvColumnMapping mapping) {
        return ResponseEntity.ok(csvAnalysisService.analyze(file, mapping));
    }

    @PostMapping(value = "/ai-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> createAiReport(
            @AuthenticationPrincipal BudgetBuddyPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestPart("mapping") CsvColumnMapping mapping) {
        CsvAnalysisResponse csvAnalysis = csvAnalysisService.analyze(file, mapping);
        return ResponseEntity.ok(aiReportService.generateCsvReport(principal.userId(), csvAnalysis.analysis()));
    }
}
