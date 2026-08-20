package com.codebasecartographer.api.service.embeddingServices.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
public class GeminiEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;
    private final String apiKey;
    private boolean enabled = false;

    public GeminiEmbeddingProvider(RestClient.Builder builder, @Value("${gemini.api.key:dummy}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = builder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models")
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(15000);
                    setReadTimeout(60000);
                }})
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("dummy") || apiKey.equals("your-gemini-api-key-here")) {
            log.warn("Gemini disabled: no API key configured");
            enabled = false;
            return;
        }
        enabled = true;
        log.info("Gemini enabled");
    }

    @Override
    public EmbeddingModel getModel() {
        return EmbeddingModel.GEMINI_EMBEDDING;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getOptimalBatchSize() {
        return 100; // Google's batchEmbedContents allows up to 100
    }

    @Override
    @RateLimiter(name = "premium-api")
    @Retry(name = "premium-api")
    public float[] embed(String text) {
        if (!enabled) {
            throw new IllegalStateException("Gemini provider is disabled: no API key");
        }

        log.debug("Generating embedding using Gemini");

        GeminiEmbeddingRequest request = new GeminiEmbeddingRequest(
                "models/gemini-embedding-2",
                new Content(List.of(new Part(text))),
                getModel().getDimension()
        );

        GeminiEmbeddingResponse response = restClient.post()
                .uri("/gemini-embedding-2:embedContent")
                .body(request)
                .retrieve()
                .body(GeminiEmbeddingResponse.class);

        if (response == null || response.embedding() == null || response.embedding().values() == null) {
            throw new RuntimeException("Received invalid response from Gemini Embedding API");
        }

        return toFloatArray(response.embedding().values());
    }

    @Override
    @RateLimiter(name = "premium-api")
    @Retry(name = "premium-api")
    public List<float[]> embedBatch(List<String> texts) {
        if (!enabled) {
            throw new IllegalStateException("Gemini provider is disabled: no API key");
        }

        log.debug("Generating batch embeddings using Gemini for {} chunks", texts.size());

        List<GeminiEmbeddingRequest> requests = texts.stream()
                .map(text -> new GeminiEmbeddingRequest(
                        "models/gemini-embedding-2",
                        new Content(List.of(new Part(text))),
                        getModel().getDimension()
                ))
                .collect(Collectors.toList());

        GeminiBatchEmbeddingRequest batchRequest = new GeminiBatchEmbeddingRequest(requests);

        GeminiBatchEmbeddingResponse response = restClient.post()
                .uri("/gemini-embedding-2:batchEmbedContents")
                .body(batchRequest)
                .retrieve()
                .body(GeminiBatchEmbeddingResponse.class);

        if (response == null || response.embeddings() == null) {
            throw new RuntimeException("Received invalid batch response from Gemini Embedding API");
        }

        return response.embeddings().stream()
                .map(e -> toFloatArray(e.values()))
                .collect(Collectors.toList());
    }

    private float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }

        if (result.length != getModel().getDimension()) {
            log.error("DIMENSION MISMATCH: Gemini returned {} dimensions, but the database requires exactly {}.",
                    result.length, getModel().getDimension());
            throw new IllegalStateException("DIMENSION MISMATCH: Gemini returned " + result.length
                    + " dimensions, but the database requires exactly " + getModel().getDimension() + ".");
        }
        return result;
    }

    private record Part(String text) {}
    private record Content(List<Part> parts) {}
    private record GeminiEmbeddingRequest(String model, Content content, Integer outputDimensionality) {}
    private record EmbeddingValues(List<Float> values) {}
    private record GeminiEmbeddingResponse(EmbeddingValues embedding) {}
    private record GeminiBatchEmbeddingRequest(List<GeminiEmbeddingRequest> requests) {}
    private record GeminiBatchEmbeddingResponse(List<EmbeddingValues> embeddings) {}
}
