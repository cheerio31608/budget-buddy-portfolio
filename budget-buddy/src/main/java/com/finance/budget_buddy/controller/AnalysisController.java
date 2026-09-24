package com.finance.budget_buddy.controller;

import com.finance.budget_buddy.dto.analysis.AnalysisResult;
import com.finance.budget_buddy.security.BudgetBuddyPrincipal;
import com.finance.budget_buddy.service.AnalysisQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisQueryService analysisQueryService;

    public AnalysisController(AnalysisQueryService analysisQueryService) {
        this.analysisQueryService = analysisQueryService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AnalysisResult> dashboard(@AuthenticationPrincipal BudgetBuddyPrincipal principal) {
        return ResponseEntity.ok(analysisQueryService.getCurrentMonth(principal.userId()));
    }

    @GetMapping("/monthly")
    public AnalysisResult monthly(@AuthenticationPrincipal BudgetBuddyPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") java.time.YearMonth month) {
        return month == null ? analysisQueryService.getCurrentMonth(principal.userId())
                : analysisQueryService.getMonth(principal.userId(), month);
    }

    @GetMapping
    public ResponseEntity<AnalysisResult> analyzePeriod(
            @AuthenticationPrincipal BudgetBuddyPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(analysisQueryService.getAnalysis(principal.userId(), start, end));
    }
}
