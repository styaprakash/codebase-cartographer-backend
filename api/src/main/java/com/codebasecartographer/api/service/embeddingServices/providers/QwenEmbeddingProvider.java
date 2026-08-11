package com.codebasecartographer.api.service.embeddingServices.providers;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.dto.request.EmbeddingRequest;
import com.codebasecartographer.api.dto.response.EmbeddingResponse;
import com.codebasecartographer.api.enums.EmbeddingModel;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class QwenEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;
    private boolean enabled = false;

    public QwenEmbeddingProvider(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("http://localhost:11434")
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(30000);
                    setReadTimeout(180000);
                }})
                .build();
    }

    @PostConstruct
    public void init() {
        RestClient pingClient = RestClient.builder()
                .baseUrl("http://localhost:11434")
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(2000);
                    setReadTimeout(2000);
                }})
                .build();
        try {
            pingClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .toBodilessEntity();
            enabled = true;
            log.info("Ollama enabled");
        } catch (Exception e) {
            enabled = false;
            log.warn("Ollama disabled: not reachable at localhost:11434");
        }
    }

    @Override
    public EmbeddingModel getModel() {
        return EmbeddingModel.QWEN3_EMBEDDING;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    @Bulkhead(name = "local-ollama")
    public float[] embed(String text) {
        if (!enabled) {
            throw new IllegalStateException("Ollama provider is disabled: not reachable at localhost:11434");
        }
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    @Bulkhead(name = "local-ollama")
    public List<float[]> embedBatch(List<String> texts) {
        if (!enabled) {
            throw new IllegalStateException("Ollama provider is disabled: not reachable at localhost:11434");
        }

        EmbeddingModel model = getModel();
        EmbeddingResponse response = restClient.post()
                .uri("/api/embed")
                .body(new EmbeddingRequest(model.getModelTag(), texts, model.getDimension()))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new RuntimeException("Ollama returned null or empty embeddings for model " + model.getModelTag());
        }

        // Validate dimension of first embedding — all should be identical
        float[] first = response.embeddings().get(0);
        if (first.length != model.getDimension()) {
            log.error("DIMENSION MISMATCH: Ollama returned {} dimensions for model '{}', but the database requires exactly 1536.",
                    first.length, model.getModelTag());
            throw new IllegalStateException("DIMENSION MISMATCH: Ollama returned " + first.length
                    + " dimensions for model '" + model.getModelTag()
                    + "', but the database requires exactly 1536.");
        }

        return response.embeddings();
    }

}
