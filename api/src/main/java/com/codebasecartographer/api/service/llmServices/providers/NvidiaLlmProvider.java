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
 * Nvidia API provider — uses Nvidia's OpenAI-compatible endpoint.
 * Supports GLM 5.2 and any future Nvidia-hosted models.
 * API key: ${nvidia.api.key} from application.yaml
 */
@Slf4j
@Component
public class NvidiaLlmProvider implements GenerativeLlmProvider {
    private final RestClient restClient;
    private final String apiKey;
    private boolean enabled = false;

    public NvidiaLlmProvider(RestClient.Builder builder,
                              @Value("${nvidia.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = builder
                .baseUrl("https://integrate.api.nvidia.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Nvidia LLM disabled: no API key configured");
            enabled = false;
            return;
        }
        enabled = true;
        log.info("Nvidia LLM enabled");
    }

    @Override
    public Set<GenerativeLlmModel> getSupportedModels() {
        return Set.of(GenerativeLlmModel.NVIDIA_GLM_5_2);
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
            throw new IllegalStateException("Nvidia provider is disabled: no API key");
        }

        log.info("Generating response using {} (tag={})", model, model.getModelTag());

        NvidiaRequest request = new NvidiaRequest(
                model.getModelTag(),
                List.of(new Message("user", prompt))
        );

        NvidiaResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(NvidiaResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new RuntimeException("Received invalid response from Nvidia API for model: " + model.getModelTag());
        }

        return response.choices().get(0).message().content();
    }

    private record Message(String role, String content) {}
    private record NvidiaRequest(String model, List<Message> messages) {}
    private record Choice(Message message) {}
    private record NvidiaResponse(List<Choice> choices) {}
}
