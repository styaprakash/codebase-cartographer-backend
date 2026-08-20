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

/**
 * OpenRouter provider — routes requests to many models via a single API key.
 * Uses the same OpenAI-compatible chat completions format.
 * Supports: DeepSeek V4, GLM 5.2, Qwen 3, and any future OpenRouter model.
 */
@Slf4j
@Component
public class OpenRouterLlmProvider implements GenerativeLlmProvider {
    private final RestClient restClient;
    private final String apiKey;
    private boolean enabled = false;

    public OpenRouterLlmProvider(RestClient.Builder builder,
                                  @Value("${openrouter.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = builder
                .baseUrl("https://openrouter.ai/api/v1")
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(15000);
                    setReadTimeout(60000);
                }})
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenRouter LLM disabled: no API key configured");
            enabled = false;
            return;
        }
        enabled = true;
        log.info("OpenRouter LLM enabled");
    }

    @Override
    public Set<GenerativeLlmModel> getSupportedModels() {
        return Set.of(
                GenerativeLlmModel.OPENROUTER_DEEPSEEK_V4_PRO,
                GenerativeLlmModel.OPENROUTER_DEEPSEEK_V4_FLASH,
                GenerativeLlmModel.OPENROUTER_GLM_5_2,
                GenerativeLlmModel.OPENROUTER_QWEN_3
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
            throw new IllegalStateException("OpenRouter provider is disabled: no API key");
        }

        log.info("Generating response using {} (tag={})", model, model.getModelTag());

        OpenRouterRequest request = new OpenRouterRequest(
                model.getModelTag(),
                List.of(new Message("user", prompt))
        );

        OpenRouterResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(OpenRouterResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new RuntimeException("Received invalid response from OpenRouter for model: " + model.getModelTag());
        }

        return response.choices().get(0).message().content();
    }

    private record Message(String role, String content) {}
    private record OpenRouterRequest(String model, List<Message> messages) {}
    private record Choice(Message message) {}
    private record OpenRouterResponse(List<Choice> choices) {}
}
