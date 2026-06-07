package com.codebasecartographer.api.dto.response;

import java.util.List;

public record EmbeddingResponse(
    String model,
    List<float[]> embeddings
) {}
