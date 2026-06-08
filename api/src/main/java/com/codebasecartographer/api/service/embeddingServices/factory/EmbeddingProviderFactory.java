package com.codebasecartographer.api.service.embeddingServices.factory;

import java.util.*;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.codebasecartographer.api.enums.EmbeddingModel;
import com.codebasecartographer.api.service.embeddingServices.providers.EmbeddingProvider;

@Slf4j
@Component
public class EmbeddingProviderFactory {
    // key = EmbeddingModel enum, value = provider instance
    private final Map<EmbeddingModel, EmbeddingProvider> providers = new EnumMap<>(EmbeddingModel.class);

    // Spring injects all @Component EmbeddingProvider beans
    public EmbeddingProviderFactory(List<EmbeddingProvider> providerList) {
        for (EmbeddingProvider provider : providerList) {
            EmbeddingModel model = provider.getModel();
            providers.put(model, provider);
            log.info("Registered embedding provider: {} (dim={}, enabled={})",
                    model, model.getDimension(), model.isEnabled());
        }
    }

    // Returns the provider for a given model enum.
    public EmbeddingProvider getProvider(EmbeddingModel model) {
        EmbeddingProvider provider = providers.get(model);

        if (provider == null) {
            throw new IllegalArgumentException(
                    "No registered provider for model: " + model);
        }

        log.info("Selected embedding provider: {} (dim={})", model, model.getDimension());
        return provider;
    }

    // Used by EmbeddingModelSelector to know what's actually available at runtime
    public Set<EmbeddingModel> getAvailableModels() {
        return Collections.unmodifiableSet(providers.keySet());
    }
}

