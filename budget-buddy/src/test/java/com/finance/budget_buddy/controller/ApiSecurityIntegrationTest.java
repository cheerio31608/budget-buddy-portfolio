package com.finance.budget_buddy.controller;

import com.finance.budget_buddy.entity.AiReport;
import com.finance.budget_buddy.entity.Transaction;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.repository.AiReportRepository;
import com.finance.budget_buddy.repository.TransactionRepository;
import com.finance.budget_buddy.security.BudgetBuddyPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AiReportRepository aiReportRepository;

    @Test
    @DisplayName("Unauthenticated APIs return JSON 401 without a Basic authentication challenge")
    void unauthenticatedApi_returnsJsonWithoutBasicChallenge() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTH002"));
    }

    @Test
    @DisplayName("Authenticated state-changing requests still require a CSRF token")
    void stateChangingApi_withoutCsrf_returnsJsonForbidden() throws Exception {
        mockMvc.perform(post("/api/transactions").with(user(principal(1L))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTH003"));
    }

    @Test
    @DisplayName("Login rotates the session id and persists the authenticated session")
    void login_rotatesAndPersistsSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String sessionIdBeforeLogin = session.getId();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user1@test.com",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("user1@test.com"))
                .andReturn();

        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(authenticatedSession).isNotNull();
        assertThat(authenticatedSession.getId()).isNotEqualTo(sessionIdBeforeLogin);

        mockMvc.perform(get("/api/auth/me").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user1@test.com"));
    }

    @Test
    @DisplayName("Transaction creation takes user ownership from the principal and hides internal fields")
    void createTransaction_usesPrincipalAndReturnsResponseDto() throws Exception {
        String transactionAt = LocalDateTime.now().minusMinutes(1).withNano(0).toString();

        mockMvc.perform(post("/api/transactions")
                        .with(user(principal(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 1,
                                  "amount": 1000.00,
                                  "transactionType": "INCOME",
                                  "description": "API contract test",
                                  "transactionAt": "%s",
                                  "vendorName": "Budget Buddy",
                                  "idempotencyKey": "api-contract-test"
                                }
                                """.formatted(transactionAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").isNumber())
                .andExpect(jsonPath("$.balanceAfter").isNumber())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());
    }

    @Test
    @DisplayName("A user cannot read another user's transaction")
    void getTransaction_forAnotherUser_returnsNotFound() throws Exception {
        Transaction transaction = transactionRepository.saveAndFlush(transaction("ownership-test"));

        mockMvc.perform(get("/api/transactions/{id}", transaction.getTransactionId())
                        .with(user(principal(999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("T001"));
    }

    @Test
    @DisplayName("Category and AI report responses do not expose persistence ownership fields")
    void listApis_returnResponseDtos() throws Exception {
        aiReportRepository.saveAndFlush(AiReport.builder()
                .userId(1L)
                .reportType("MONTHLY")
                .reportContent("DTO contract report")
                .build());

        mockMvc.perform(get("/api/categories").with(user(principal(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").isNumber())
                .andExpect(jsonPath("$[0].userId").doesNotExist());

        mockMvc.perform(get("/api/ai/reports").with(user(principal(1L))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DTO contract report")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"userId\""))));
    }

    private BudgetBuddyPrincipal principal(Long userId) {
        return BudgetBuddyPrincipal.user(userId, "user" + userId + "@test.com", "password");
    }

    private Transaction transaction(String idempotencyKey) {
        return Transaction.builder()
                .userId(1L)
                .categoryId(2L)
                .amount(new BigDecimal("1000.00"))
                .balanceBefore(new BigDecimal("10000.00"))
                .balanceAfter(new BigDecimal("9000.00"))
                .transactionType(TransactionType.EXPENSE)
                .description("ownership test")
                .transactionAt(LocalDateTime.now().minusMinutes(1))
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
