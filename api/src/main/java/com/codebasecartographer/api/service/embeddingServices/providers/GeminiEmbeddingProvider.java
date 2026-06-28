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
public class GeminiEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;

    public GeminiEmbeddingProvider(RestClient.Builder builder, @Value("${gemini.api.key:dummy}") String apiKey) {
        this.restClient = builder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    @Override
    public EmbeddingModel getModel() {
        return EmbeddingModel.GEMINI_EMBEDDING; // Or whichever enum maps to this
    }

    @Override
    @RateLimiter(name = "premium-api")
    @Retry(name = "premium-api")
    public float[] embed(String text) {
        log.debug("Generating embedding using Gemini (text-embedding-004)");

        GeminiEmbeddingRequest request = new GeminiEmbeddingRequest(
                "models/text-embedding-004",
                new Content(List.of(new Part(text)))
        );

        GeminiEmbeddingResponse response = restClient.post()
                .uri("/text-embedding-004:embedContent")
                .body(request)
                .retrieve()
                .body(GeminiEmbeddingResponse.class);

        if (response == null || response.embedding() == null || response.embedding().values() == null) {
            throw new RuntimeException("Received invalid response from Gemini Embedding API");
        }

        List<Float> values = response.embedding().values();
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private record Part(String text) {}
    private record Content(List<Part> parts) {}
    private record GeminiEmbeddingRequest(String model, Content content) {}
    private record EmbeddingValues(List<Float> values) {}
    private record GeminiEmbeddingResponse(EmbeddingValues embedding) {}
}
