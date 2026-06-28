package com.codebasecartographer.api.service.embeddingServices.providers;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.enums.EmbeddingModel;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OpenRouterEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;

    public OpenRouterEmbeddingProvider(RestClient.Builder builder, @Value("${openrouter.api.key:dummy}") String apiKey) {
        this.restClient = builder
                .baseUrl("https://openrouter.ai/api/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("HTTP-Referer", "http://localhost:3000")
                .build();
    }

    @Override
    public EmbeddingModel getModel() {
        return EmbeddingModel.OPENROUTER_QWEN_EMBEDDING;
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
        return response.data().stream()
                .map(data -> {
                    float[] result = new float[data.embedding().size()];
                    for (int i = 0; i < data.embedding().size(); i++) {
                        result[i] = data.embedding().get(i);
                    }
                    return result;
                })
                .toList();
    }

    private record OpenRouterEmbeddingRequest(String model, List<String> input) {}
    private record EmbeddingData(List<Float> embedding, int index) {}
    private record OpenRouterEmbeddingResponse(List<EmbeddingData> data) {}
}
