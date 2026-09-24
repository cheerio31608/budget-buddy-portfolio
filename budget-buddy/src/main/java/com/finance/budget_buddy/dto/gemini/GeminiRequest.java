package com.finance.budget_buddy.dto.gemini;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class GeminiRequest {
    private List<Content> contents;
    private GenerationConfig generationConfig;

    public record GenerationConfig(int maxOutputTokens) {}

    @Getter
    @AllArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @Getter
    @AllArgsConstructor
    public static class Part {
        private String text;
    }

    public static GeminiRequest fromText(String text) {
        return new GeminiRequest(List.of(new Content(List.of(new Part(text)))), new GenerationConfig(4096));
    }
}
