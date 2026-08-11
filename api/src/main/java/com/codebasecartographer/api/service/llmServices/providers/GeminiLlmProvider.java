package com.codebasecartographer.api.service.llmServices.providers;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.enums.GenerativeLlmModel;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GeminiLlmProvider implements GenerativeLlmProvider {
    private final RestClient restClient;
    private final String apiKey;
    private boolean enabled = false;

    public GeminiLlmProvider(RestClient.Builder builder, @Value("${gemini.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = builder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini LLM disabled: no API key configured");
            enabled = false;
            return;
        }
        enabled = true;
        log.info("Gemini LLM enabled");
    }

    @Override
    public Set<GenerativeLlmModel> getSupportedModels() {
        return Set.of(
                GenerativeLlmModel.GEMINI_1_5_FLASH,
                GenerativeLlmModel.GEMINI_3_5_FLASH,
                GenerativeLlmModel.GEMINI_2_5_FLASH,
                GenerativeLlmModel.GEMINI_2_0_FLASH,
                GenerativeLlmModel.GEMINI_2_5_FLASH_LITE,
                GenerativeLlmModel.GEMINI_3_1_FLASH_LITE_PREVIEW
        );
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    @RateLimiter(name = "premium-api")
    @Retry(name = "premium-api")
    public String generateResponse(GenerativeLlmModel model, String prompt) {
        if (!enabled) {
            throw new IllegalStateException("Gemini provider is disabled: no API key");
        }

        log.info("Generating response using {} (tag={})", model, model.getModelTag());

        GeminiRequest request = new GeminiRequest(List.of(new Content(List.of(new Part(prompt)))));

        GeminiResponse response = restClient.post()
                .uri("/{modelTag}:generateContent", model.getModelTag())
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new RuntimeException("Received invalid response from Gemini API");
        }

        Candidate firstCandidate = response.candidates().get(0);
        if (firstCandidate.content() == null || firstCandidate.content().parts() == null || firstCandidate.content().parts().isEmpty()) {
            throw new RuntimeException("Received invalid response from Gemini API (missing content/parts)");
        }

        return firstCandidate.content().parts().get(0).text();
    }

    private record Part(String text) {}
    private record Content(List<Part> parts) {}
    private record GeminiRequest(List<Content> contents) {}
    private record Candidate(Content content) {}
    private record GeminiResponse(List<Candidate> candidates) {}
}
