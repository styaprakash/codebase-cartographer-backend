package com.codebasecartographer.api.service.embeddingServices.providers;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.enums.EmbeddingModel;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OpenRouterEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;
    private final String apiKey;

    public OpenRouterEmbeddingProvider(RestClient.Builder builder, @Value("${openrouter.api.key:dummy}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = builder
                .baseUrl("https://openrouter.ai/api/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("HTTP-Referer", "http://localhost:3000")
                .build();
    }

    @PostConstruct
    public void validateApiKey() {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("dummy") || apiKey.equals("your-openrouter-api-key-here")) {
            log.error("CRITICAL: OpenRouter API key is missing. Set OPENROUTER_API_KEY env var.");
            throw new IllegalStateException("OpenRouter API key is not configured.");
        }
        // Mask the key for logging: show first 8 and last 4 characters
        String masked = apiKey.substring(0, Math.min(8, apiKey.length()))
                + "..." + apiKey.substring(Math.max(0, apiKey.length() - 4));
        log.info("OpenRouter API key loaded: {} (length={})", masked, apiKey.length());

        // Live validation — single-text embed call to confirm key is active
        try {
            float[] probe = embed("health check");
            log.info("OpenRouter API key validated — embedding returned {} dimensions", probe.length);
        } catch (Exception e) {
            log.error("CRITICAL: OpenRouter API key is INVALID ({}). "
                    + "Set a valid OPENROUTER_API_KEY env var. "
                    + "The app will start but OpenRouter embeddings will fail at runtime.",
                    e.getMessage());
        }
    }

    @Override
    public EmbeddingModel getModel() {
        return EmbeddingModel.OPENROUTER_EMBEDDING_1536;
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    @RateLimiter(name = "premium-api")
    @Retry(name = "premium-api")
    public List<float[]> embedBatch(List<String> texts) {
        log.debug("Generating batch embeddings using OpenRouter ({})", getModel().getModelTag());

        OpenRouterEmbeddingRequest request = new OpenRouterEmbeddingRequest(getModel().getModelTag(), texts);

        OpenRouterEmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .body(request)
                .retrieve()
                .body(OpenRouterEmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new RuntimeException("Received invalid response from OpenRouter API");
        }

        // Map the List of EmbeddingData to a List of float[]
        List<float[]> embeddings = response.data().stream()
                .map(data -> {
                    float[] result = new float[data.embedding().size()];
                    for (int i = 0; i < data.embedding().size(); i++) {
                        result[i] = data.embedding().get(i);
                    }
                    return result;
                })
                .toList();

        // Validate dimension of first embedding — all should be identical
        if (!embeddings.isEmpty() && embeddings.get(0).length != getModel().getDimension()) {
            log.error("DIMENSION MISMATCH: OpenRouter returned {} dimensions, but the database requires exactly 1536.",
                    embeddings.get(0).length);
            throw new IllegalStateException("DIMENSION MISMATCH: OpenRouter returned " + embeddings.get(0).length
                    + " dimensions, but the database requires exactly 1536.");
        }

        return embeddings;
    }

    private record OpenRouterEmbeddingRequest(String model, List<String> input) {}
    private record EmbeddingData(List<Float> embedding, int index) {}
    private record OpenRouterEmbeddingResponse(List<EmbeddingData> data) {}
}
