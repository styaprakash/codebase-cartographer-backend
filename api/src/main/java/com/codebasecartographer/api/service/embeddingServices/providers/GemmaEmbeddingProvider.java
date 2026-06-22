package com.codebasecartographer.api.service.embeddingServices.providers;

import java.util.List;

import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.dto.request.EmbeddingRequest;
import com.codebasecartographer.api.dto.response.EmbeddingResponse;
import com.codebasecartographer.api.enums.EmbeddingModel;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;

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
    // Bulkhead limits concurrent calls to the Local Ollama instance.
    // Annotated on both embed and embedBatch to ensure AOP proxies intercept external calls to either method.
    @Bulkhead(name = "local-ollama")
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingModel model = getModel();
        EmbeddingResponse response = restClient.post()
                .uri("/api/embed")
                .body(new EmbeddingRequest(model.getModelTag(), texts, model.getDimension()))
                .retrieve()
                .body(EmbeddingResponse.class);

        return response.embeddings();
    }

    @Override
    // Bulkhead limits concurrent calls to the Local Ollama instance.
    // Annotated on both embed and embedBatch to ensure AOP proxies intercept external calls to either method.
    @Bulkhead(name = "local-ollama")
    public float[] embed(String text) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'embed'");
    }
}
