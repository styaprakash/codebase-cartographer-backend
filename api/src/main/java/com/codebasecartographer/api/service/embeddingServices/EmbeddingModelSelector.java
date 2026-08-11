package com.codebasecartographer.api.service.embeddingServices;

import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.codebasecartographer.api.enums.EmbeddingModel;
import com.codebasecartographer.api.service.embeddingServices.factory.EmbeddingProviderFactory;

@Slf4j
@Component
public class EmbeddingModelSelector {
    private final EmbeddingProviderFactory providerFactory;

    public EmbeddingModelSelector(EmbeddingProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    /**
     * Selects an embedding model for a new repository.
     * Strategy: prefer local Ollama (QWEN3_EMBEDDING) for cost/latency,
     * then fall back to remote APIs in enum order.
     * Future: add load-based, latency-based, or round-robin strategies.
     */
    public EmbeddingModel selectModel() {
        Set<EmbeddingModel> available = providerFactory.getAvailableModels();

        if (available.isEmpty()) {
            throw new IllegalStateException(
                    "No embedding providers available. Configure at least one of: "
                    + "OPENROUTER_API_KEY, GEMINI_API_KEY, or start Ollama on port 11434");
        }

        // Prefer local Ollama first for cost and latency benefits
        EmbeddingModel localModel = EmbeddingModel.QWEN3_EMBEDDING;
        if (localModel.isEnabled() && available.contains(localModel)) {
            log.info("Selected embedding model (local priority): {}", localModel);
            return localModel;
        }

        // Fall back to remote APIs in enum order
        for (EmbeddingModel model : EmbeddingModel.values()) {
            if (model.isEnabled() && available.contains(model)) {
                log.info("Selected embedding model: {}", model);
                return model;
            }
        }

        throw new IllegalStateException(
                "No embedding providers available. Configure at least one of: "
                + "OPENROUTER_API_KEY, GEMINI_API_KEY, or start Ollama on port 11434");
    }
}
