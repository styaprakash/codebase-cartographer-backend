package com.codebasecartographer.api.service.embeddingServices;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.dto.request.EmbeddingRequest;
import com.codebasecartographer.api.dto.response.EmbeddingResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OllamaEmbeddingService implements EmbeddingService {
    private final RestClient restClient;

    public OllamaEmbeddingService(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("http://localhost:11434")
                .build();
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response =
                restClient.post()
                        .uri("/api/embed")
                        .body(new EmbeddingRequest(
                                "qwen3-embedding",
                                text,
                                1536
                        ))
                        .retrieve()
                        .body(EmbeddingResponse.class);

        float[] embedding = response.embeddings().get(0);
        log.debug("Embedding dimension = {}", embedding.length);

        return embedding;
    }
}
