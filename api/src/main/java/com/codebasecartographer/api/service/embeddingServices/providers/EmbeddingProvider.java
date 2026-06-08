package com.codebasecartographer.api.service.embeddingServices.providers;

import java.util.ArrayList;
import java.util.List;

import com.codebasecartographer.api.enums.EmbeddingModel;

/**
 * Contract for all embedding providers.
 * Each provider serves exactly one EmbeddingModel.
 */
public interface EmbeddingProvider {
    // Which enum constant this provider serves
    EmbeddingModel getModel();

    // Single text → vector
    float[] embed(String text);

    // Batch embedding — default falls back to sequential single calls.
    // Override in providers that support native batch APIs.
    default List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }
}

