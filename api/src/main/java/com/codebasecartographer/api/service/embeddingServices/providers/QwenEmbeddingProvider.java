package com.codebasecartographer.api.service.embeddingServices.providers;

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

    @Override
    public float[] embed(String text) {
        EmbeddingModel model = getModel();
        EmbeddingResponse response = restClient.post()
                .uri("/api/embed")
                .body(new EmbeddingRequest(model.getModelTag(), text, model.getDimension()))
                .retrieve()
                .body(EmbeddingResponse.class);

        return response.embeddings().get(0);
    }
}

