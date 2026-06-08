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
     * Strategy: return the first enabled model with a registered provider.
     * Future: add load-based, latency-based, or round-robin strategies.
     */
    public EmbeddingModel selectModel() {
        Set<EmbeddingModel> available = providerFactory.getAvailableModels();

        // Prefer enabled models that have a registered provider
        for (EmbeddingModel model : EmbeddingModel.values()) {
            if (model.isEnabled() && available.contains(model)) {
                log.info("Selected embedding model: {}", model);
                return model;
            }
        }

        throw new IllegalStateException(
                "No enabled embedding model with a registered provider. "
                + "Available providers: " + available);
    }
}
