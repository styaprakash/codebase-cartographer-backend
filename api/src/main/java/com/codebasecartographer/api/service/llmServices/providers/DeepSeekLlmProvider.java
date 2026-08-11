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
 * DeepSeek v4 provider — uses the DeepSeek Chat Completions API.
 * Resilience annotations match the premium-api tier established for OpenAI.
 */
@Slf4j
@Component
public class DeepSeekLlmProvider implements GenerativeLlmProvider {
    private final RestClient restClient;
    private final String apiKey;
    private boolean enabled = false;

    public DeepSeekLlmProvider(RestClient.Builder builder, @Value("${deepseek.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = builder
                .baseUrl("https://api.deepseek.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DeepSeek LLM disabled: no API key configured");
            enabled = false;
            return;
        }
        enabled = true;
        log.info("DeepSeek LLM enabled");
    }

    @Override
    public Set<GenerativeLlmModel> getSupportedModels() {
        return Set.of(GenerativeLlmModel.DEEPSEEK_V4);
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
            throw new IllegalStateException("DeepSeek provider is disabled: no API key");
        }

        log.info("Generating response using {} (tag={})", model, model.getModelTag());

        DeepSeekRequest request = new DeepSeekRequest(
                model.getModelTag(),
                List.of(new Message("user", prompt)),
                false
        );

        DeepSeekResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(DeepSeekResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new RuntimeException("Received invalid response from DeepSeek");
        }

        return response.choices().get(0).message().content();
    }

    private record Message(String role, String content) {}
    private record DeepSeekRequest(String model, List<Message> messages, boolean stream) {}
    private record Choice(Message message) {}
    private record DeepSeekResponse(List<Choice> choices) {}
}
