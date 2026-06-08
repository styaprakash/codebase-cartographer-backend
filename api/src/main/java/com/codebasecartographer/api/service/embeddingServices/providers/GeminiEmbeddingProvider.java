package com.codebasecartographer.api.service.embeddingServices.providers;

import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.enums.EmbeddingModel;

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
    public float[] embed(String text) {
        throw new UnsupportedOperationException("Gemini embedding provider not implemented yet");
    }
}

