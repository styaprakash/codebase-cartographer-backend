package com.codebasecartographer.api.service.embeddingServices.providers;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.dto.request.EmbeddingRequest;
import com.codebasecartographer.api.dto.response.EmbeddingResponse;
import com.codebasecartographer.api.enums.EmbeddingModel;

@Component
public class QwenEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;

    public QwenEmbeddingProvider(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("http://localhost:11434")
                .build();
    }

    @Override
    public EmbeddingModel getModel() {
        return EmbeddingModel.QWEN3_EMBEDDING;
    }

    // Single text embedding (uses batch with single item)
    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    // Batch embedding
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingModel model = getModel();
        EmbeddingResponse response = restClient.post()
                .uri("/api/embed")
                .body(new EmbeddingRequest(model.getModelTag(), texts, model.getDimension()))
                .retrieve()
                .body(EmbeddingResponse.class);
        return response.embeddings();
    }

}
