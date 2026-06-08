package com.codebasecartographer.api.service.embeddingServices.providers;

import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.enums.EmbeddingModel;

// Not @Component — this provider is disabled until the NV-Embed model is pulled.
// Enable by adding @Component and setting NV_EMBED_V2.enabled = true.
public class NVEmbedEmbeddingProvider implements EmbeddingProvider {
    private final RestClient restClient;

    public NVEmbedEmbeddingProvider(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("http://localhost:11434")
                .build();
    }

    @Override
    public EmbeddingModel getModel() {
        return EmbeddingModel.NV_EMBED_V2;
    }

    @Override
    public float[] embed(String text) {
        throw new UnsupportedOperationException(
                "NV-Embed-v2 provider not implemented yet");
    }
}

