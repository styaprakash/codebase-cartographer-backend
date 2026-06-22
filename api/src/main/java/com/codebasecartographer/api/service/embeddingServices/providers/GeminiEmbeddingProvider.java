package com.codebasecartographer.api.service.embeddingServices.providers;

import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.enums.EmbeddingModel;
import java.util.List;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

// Not @Component — this provider is disabled.
// When implementing: change base URL to Google AI Studio / Vertex AI,
// update request/response format, add API key configuration.
public class GeminiEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;

    public GeminiEmbeddingProvider(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("http://localhost:11434") // TODO: replace with Gemini API URL
                .build();
    }

    @Override
    public EmbeddingModel getModel() {
        return EmbeddingModel.GEMINI_EMBEDDING;
    }

    @Override
    // RateLimiter caps requests per minute/second.
    // Retry automatically retries on 429 Too Many Requests with exponential backoff.
    // Both are annotated on embed and embedBatch to ensure AOP proxies intercept external calls.
    @RateLimiter(name = "premium-api")
    @Retry(name = "premium-api")
    public float[] embed(String text) {
        throw new UnsupportedOperationException("Gemini embedding provider not implemented yet");
    }

    @Override
    // RateLimiter caps requests per minute/second.
    // Retry automatically retries on 429 Too Many Requests with exponential backoff.
    // Both are annotated on embed and embedBatch to ensure AOP proxies intercept external calls.
    @RateLimiter(name = "premium-api")
    @Retry(name = "premium-api")
    public List<float[]> embedBatch(List<String> texts) {
        return EmbeddingProvider.super.embedBatch(texts);
    }
}

