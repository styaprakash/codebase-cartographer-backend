package com.codebasecartographer.api.service.embeddingServices.providers;

import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.dto.request.EmbeddingRequest;
import com.codebasecartographer.api.dto.response.EmbeddingResponse;
import com.codebasecartographer.api.enums.EmbeddingModel;

// Not @Component — this provider is disabled until the Gemma model is pulled.
// Enable by adding @Component and setting EMBEDDING_GEMMA.enabled = true.
public class GemmaEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;

    public GemmaEmbeddingProvider(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("http://localhost:11434")
                .build();
    }

    @Override
    public EmbeddingModel getModel() {
        return EmbeddingModel.EMBEDDING_GEMMA;
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

