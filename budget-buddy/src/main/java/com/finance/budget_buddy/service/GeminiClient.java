package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.gemini.GeminiRequest;
import com.finance.budget_buddy.dto.gemini.GeminiResponse;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GeminiClient {

    private final RestClient restClient;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    public GeminiClient() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(40));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Gemini API에 텍스트 프롬프트를 전송하고 응답을 받습니다.
     */
    public String generateContent(String prompt) {
        ensureConfigured();
        GeminiRequest request = GeminiRequest.fromText(prompt);
        try {
            GeminiResponse response = restClient.post()
                    .uri(apiUrl)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GeminiResponse.class);

            if (response == null || response.getText().isBlank()) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }
            return response.getText();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    /** Checks local configuration before an attempt is charged against the AI quota. */
    public void ensureConfigured() {
        if (apiKey == null || apiKey.isBlank() || "dummy-api-key".equals(apiKey)) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
    }
}
