package com.finance.budget_buddy.controller;

import com.finance.budget_buddy.security.BudgetBuddyPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Malformed JSON values return a safe 400 response instead of an internal error")
    void malformedJson_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .with(user(principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 2,
                                  "amount": 1000,
                                  "transactionType": "UNKNOWN",
                                  "transactionAt": "2026-09-01T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("Missing required query parameters return a structured 400 response")
    void missingQueryParameter_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/analysis")
                        .param("start", "2026-09-01")
                        .with(user(principal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("A read request cannot call the report generation endpoint")
    void sideEffectEndpoint_withGet_returnsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/ai/report/monthly").with(user(principal())))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("Unknown authenticated API paths return a structured 404 response")
    void unknownApiPath_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").with(user(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C005"));
    }

    private BudgetBuddyPrincipal principal() {
        return BudgetBuddyPrincipal.user(1L, "user1@test.com", "password");
    }
}
