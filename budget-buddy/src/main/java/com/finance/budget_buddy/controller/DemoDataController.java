package com.finance.budget_buddy.controller;

import com.finance.budget_buddy.security.BudgetBuddyPrincipal;
import com.finance.budget_buddy.service.DemoDataService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Local portfolio helper; it is not enabled in production profiles. */
@RestController
@Profile({"local", "h2", "compose"})
@RequestMapping("/api/demo")
public class DemoDataController {

    private final DemoDataService demoDataService;

    public DemoDataController(DemoDataService demoDataService) {
        this.demoDataService = demoDataService;
    }

    @PostMapping("/seed")
    public ResponseEntity<DemoSeedResponse> seed(@AuthenticationPrincipal BudgetBuddyPrincipal principal,
                                                 @RequestParam(defaultValue = "30") int count) {
        DemoDataService.DemoSeedResult result = demoDataService.createScenario(principal.userId(), count);
        return ResponseEntity.ok(new DemoSeedResponse(
                result.requestedCount(),
                result.createdCount(),
                result.requestedCount() + "건의 데모 거래를 생성했습니다."));
    }

    public record DemoSeedResponse(int requestedCount, int createdCount, String message) {
    }
}
