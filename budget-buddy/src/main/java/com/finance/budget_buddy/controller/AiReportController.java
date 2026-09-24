package com.finance.budget_buddy.controller;

import com.finance.budget_buddy.dto.ai.AiReportResponse;
import com.finance.budget_buddy.security.BudgetBuddyPrincipal;
import com.finance.budget_buddy.service.AiReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiReportController {

    private final AiReportService aiReportService;

    /** Generates and stores a monthly report; GET endpoints remain read-only. */
    @PostMapping("/report/monthly")
    public ResponseEntity<String> createMonthlyReport(@AuthenticationPrincipal BudgetBuddyPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM") java.time.YearMonth month) {
        return ResponseEntity.ok(month == null ? aiReportService.generateMonthlyReport(principal.userId())
                : aiReportService.generateMonthlyReport(principal.userId(), month));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<AiReportResponse>> getReports(
            @AuthenticationPrincipal BudgetBuddyPrincipal principal) {
        List<AiReportResponse> reports = aiReportService.getReports(principal.userId()).stream()
                .map(AiReportResponse::from)
                .toList();
        return ResponseEntity.ok(reports);
    }
}
