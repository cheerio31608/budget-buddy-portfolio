package com.finance.budget_buddy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.budget_buddy.repository.UserRepository;
import com.finance.budget_buddy.service.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the real production filter chain with an isolated migrated H2 database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:web_deployment;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.hikari.connection-init-sql=", "app.frontend-origin=https://budget.example.com",
        "app.ai.daily-global-limit=100", "app.ai.daily-user-limit=1", "app.ai.cooldown-seconds=0"
})
@ActiveProfiles({"test", "prod"})
@AutoConfigureMockMvc
class WebDeploymentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired com.finance.budget_buddy.service.AiQuotaService quota;
    @MockitoBean GeminiClient gemini;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry props) {
        props.add("app.jwt.secret", () -> UUID.randomUUID().toString() + UUID.randomUUID());
    }

    @Test void registerHashesPasswordAndLoginLogoutRevokesToken() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String token = register(email);
        var user = users.findByEmail(email).orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}").isNotEqualTo("a-secure-password");
        assertThat(passwords.matches("a-secure-password", user.getPasswordHash())).isTrue();
        mvc.perform(get("/api/web-auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/web-auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", "bad-password")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/web-auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", "a-secure-password")))).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        mvc.perform(post("/api/web-auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/web-auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email.toUpperCase(), "password", "a-secure-password")))).andExpect(status().isConflict());
    }

    @Test void productionBlocksAnonymousTamperedTokensAndDemoLogin() throws Exception {
        for (String path : new String[]{"/api/transactions", "/api/categories", "/api/analysis/monthly", "/api/ai/reports"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"));
        }
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/web-auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user1@test.com\",\"password\":\"password\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/web-auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"invalid\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test void usersCannotReadOrWriteEachOthersTransactionsCategoriesAndReports() throws Exception {
        String a = register(UUID.randomUUID() + "@example.com"), b = register(UUID.randomUUID() + "@example.com");
        long categoryA = incomeCategory(a), categoryB = incomeCategory(b);
        assertThat(categoryA).isNotEqualTo(categoryB);
        var created = createIncome(a, categoryA, "one-request");
        var repeated = createIncome(a, categoryA, "one-request");
        assertThat(repeated.get("transactionId")).isEqualTo(created.get("transactionId"));
        mvc.perform(get("/api/transactions/" + created.get("transactionId").asLong()).header("Authorization", "Bearer " + b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions?userId=1").header("Authorization", "Bearer " + b)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/transactions").header("Authorization", "Bearer " + b).contentType(MediaType.APPLICATION_JSON).content(incomeBody(categoryA, "foreign-category"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/analysis/monthly").header("Authorization", "Bearer " + b)).andExpect(status().isOk()).andExpect(jsonPath("$.totalIncome").value(0));
        when(gemini.generateContent(anyString())).thenReturn("월간 소비 요약: 수입을 기록했습니다.");
        mvc.perform(post("/api/ai/report/monthly?month=" + YearMonth.now()).header("Authorization", "Bearer " + a)).andExpect(status().isOk());
        mvc.perform(get("/api/ai/reports").header("Authorization", "Bearer " + b)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/ai/reports").header("Authorization", "Bearer " + a)).andExpect(status().isOk()).andExpect(jsonPath("$[0].reportMonth").value(YearMonth.now().toString()));
        mvc.perform(post("/api/ai/report/monthly").header("Authorization", "Bearer " + a)).andExpect(status().isTooManyRequests());
        verify(gemini, times(1)).generateContent(anyString());
    }

    @Test void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/transactions").header("Origin", "https://budget.example.com").header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://budget.example.com"));
        mvc.perform(options("/api/transactions").header("Origin", "https://untrusted.example.com").header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    @Test void simultaneousAiAttemptsCannotExceedTheUserLimit() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email);
        Long userId = users.findByEmail(email).orElseThrow().getUserId();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        var ready = new java.util.concurrent.CountDownLatch(4);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 4; i++) results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
                try { quota.reserve(userId); return true; }
                catch (com.finance.budget_buddy.exception.BusinessException exception) {
                    assertThat(exception.getErrorCode()).isEqualTo(com.finance.budget_buddy.exception.ErrorCode.AI_LIMIT_EXCEEDED);
                    return false;
                }
            }));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            int successes = 0;
            for (var result : results) if (result.get(10, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test void csvImportsAtomicallyDeduplicatesAndUsesAuthenticatedOwner() throws Exception {
        String a = register(UUID.randomUUID() + "@example.com"), b = register(UUID.randomUUID() + "@example.com");
        String date = LocalDate.now().minusDays(1).toString();
        String content = "date,description,category,amount,type\n" + date + ",급여,급여,1000,INCOME\n" + date + ",카페,커피,200,EXPENSE";
        mvc.perform(multipart("/api/csv/import").file(csv(content)).file(mapping()).header("Authorization", "Bearer " + a)).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(2));
        mvc.perform(multipart("/api/csv/import").file(csv(content)).file(mapping()).header("Authorization", "Bearer " + a)).andExpect(status().isOk()).andExpect(jsonPath("$.alreadyImported").value(2));
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + b)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/analysis/monthly").header("Authorization", "Bearer " + a)).andExpect(jsonPath("$.currentBalance").value(800));
        String failing = "date,description,category,amount,type\n" + date + ",급여,급여,100,INCOME\n" + date + ",카페,새카테고리,99999,EXPENSE";
        mvc.perform(multipart("/api/csv/import").file(csv(failing)).file(mapping()).header("Authorization", "Bearer " + b)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + b)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + b)).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/analysis/monthly").header("Authorization", "Bearer " + b)).andExpect(jsonPath("$.currentBalance").value(0));
        mvc.perform(multipart("/api/csv/analyze").file(csv("date,description,category,amount,type\n2026-02-30,a,b,1.234,EXPENSE")).file(mapping()).header("Authorization", "Bearer " + b)).andExpect(status().isOk()).andExpect(jsonPath("$.validation.errorRows").value(1));
        mvc.perform(multipart("/api/csv/preview").file(csv(content))).andExpect(status().isUnauthorized());
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "CSV format {0}: automatic mapping through dashboard")
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void automaticCsvMappingFlowsThroughImportAndDashboard(int format) throws Exception {
        String token = register(UUID.randomUUID() + "@example.com");
        String date = LocalDate.now().withDayOfMonth(1).toString();
        String content = switch (format) {
            case 0 -> "date,description,category,amount,type\n"
                    + date + ",Salary,급여,10000,INCOME\n" + date + ",Cafe,카페,2500,EXPENSE";
            case 1 -> "\uFEFF구분,금액,사용처,카테고리명,거래일자,잔액,비고\r\n"
                    + "수입,10000,회사,급여," + date + ",10000,무시할 값\r\n"
                    + "지출,2500,카페,카페," + date + ",7500,무시할 값";
            case 2 -> "발생시각;사용처;분류;사용액;흐름\n"
                    + date.replace('-', '/') + ";회사;급여;10000;credit\n"
                    + date.replace('-', '/') + ";카페;카페;2500;debit";
            case 3 -> "거래일시\t상호명\t분류\t결제금액\t입출금구분\n"
                    + date + " 00:00:00\t회사\t급여\t10000\t입금\n"
                    + date + " 00:00:00\t카페\t카페\t2500\t출금";
            case 4 -> "date,description,category,amount,type\n"
                    + date + ",Salary,급여,\"10,000\",INCOME\n"
                    + date + ",\"Cafe, Seoul\",카페,\"2,500\",EXPENSE";
            default -> "date,description,category,amount,흐름\n"
                    + date + ",Salary,급여,10000,+\n"
                    + date + ",Cafe,카페,2500,-";
        };
        byte[] bytes = content.getBytes(format == 3 ? java.nio.charset.Charset.forName("MS949") : StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "format.csv", "text/csv", bytes);
        JsonNode preview = json.readTree(mvc.perform(multipart("/api/csv/preview").file(file)
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode suggested = preview.get("suggestedMapping");
        for (String field : new String[]{"date", "category", "amount", "type"}) {
            assertThat(suggested.hasNonNull(field)).as("Required mapping: %s", field).isTrue();
        }
        var selectedMapping = new MockMultipartFile("mapping", "", "application/json", json.writeValueAsBytes(suggested));
        mvc.perform(multipart("/api/csv/analyze").file(file).file(selectedMapping)
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.errorRows").value(0))
                .andExpect(jsonPath("$.analysis.totalIncome").value(10000))
                .andExpect(jsonPath("$.analysis.totalExpense").value(2500));
        // Preview and temporary analysis must not change the account's persisted balance.
        mvc.perform(get("/api/analysis/monthly").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentBalance").value(0));
        mvc.perform(multipart("/api/csv/import").file(file).file(selectedMapping)
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2));
        mvc.perform(multipart("/api/csv/import").file(file).file(selectedMapping)
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyImported").value(2));
        mvc.perform(get("/api/analysis/monthly").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(7500))
                .andExpect(jsonPath("$.totalIncome").value(10000))
                .andExpect(jsonPath("$.totalExpense").value(2500))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.categoryExpenses[0].category").value("카페"))
                .andExpect(jsonPath("$.categoryExpenses[0].amount").value(2500));
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    private String register(String email) throws Exception {
        return json.readTree(mvc.perform(post("/api/web-auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "a-secure-password"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
    private long incomeCategory(String token) throws Exception {
        JsonNode categories = json.readTree(mvc.perform(get("/api/categories").header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        for (var c : categories) if (c.get("type").asText().equals("INCOME")) return c.get("categoryId").asLong();
        throw new AssertionError("No income category");
    }
    private JsonNode createIncome(String token, long category, String key) throws Exception {
        return json.readTree(mvc.perform(post("/api/transactions").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(incomeBody(category, key))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private String incomeBody(long category, String key) throws Exception {
        return json.writeValueAsString(Map.of("userId", 1, "categoryId", category, "amount", 1000, "transactionType", "INCOME", "transactionAt", LocalDate.now().atStartOfDay().toString(), "idempotencyKey", key));
    }
    private MockMultipartFile csv(String content) { return new MockMultipartFile("file", "book.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8)); }
    private MockMultipartFile mapping() { return new MockMultipartFile("mapping", "", "application/json", "{\"date\":\"date\",\"description\":\"description\",\"category\":\"category\",\"amount\":\"amount\",\"type\":\"type\"}".getBytes(StandardCharsets.UTF_8)); }
}
