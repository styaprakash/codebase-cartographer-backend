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
            if (provider.isEnabled()) {
                providers.put(model, provider);
                log.info("Registered embedding provider: {} (dim={}, enabled=true)", model, model.getDimension());
            } else {
                log.warn("Skipped disabled embedding provider: {} (enabled=false)", model);
            }
        }

        if (providers.isEmpty()) {
            log.error("No embedding providers available. Configure at least one of: "
                    + "OPENROUTER_API_KEY, GEMINI_API_KEY, or start Ollama on port 11434");
        } else {
            log.info("Enabled embedding providers: {}", providers.keySet());
        }
    }

    // Returns the provider for a given model enum.
    public EmbeddingProvider getProvider(EmbeddingModel model) {
        EmbeddingProvider provider = providers.get(model);

        if (provider == null) {
            // Fallback to first available enabled provider
            if (!providers.isEmpty()) {
                EmbeddingModel fallbackModel = providers.keySet().iterator().next();
                EmbeddingProvider fallbackProvider = providers.get(fallbackModel);
                log.warn("Requested model {} not available, falling back to: {}", model, fallbackModel);
                return fallbackProvider;
            }
            throw new IllegalStateException(
                    "No embedding providers available. Configure at least one of: "
                    + "OPENROUTER_API_KEY, GEMINI_API_KEY, or start Ollama on port 11434");
        }

        log.info("Selected embedding provider: {} (dim={})", model, model.getDimension());
        return provider;
    }

    // Used by EmbeddingModelSelector to know what's actually available at runtime
    public Set<EmbeddingModel> getAvailableModels() {
        return Collections.unmodifiableSet(providers.keySet());
    }
}
