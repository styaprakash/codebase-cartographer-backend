package com.codebasecartographer.api.service.llmServices.factory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.codebasecartographer.api.enums.GenerativeLlmModel;
import com.codebasecartographer.api.service.llmServices.providers.GenerativeLlmProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LlmProviderFactory {
    // key = GenerativeLlmModel enum, value = provider instance
    private final Map<GenerativeLlmModel, GenerativeLlmProvider> providers = new EnumMap<>(GenerativeLlmModel.class);

    // Spring injects all @Component GenerativeLlmProvider beans
    public LlmProviderFactory(List<GenerativeLlmProvider> providerList) {
        for (GenerativeLlmProvider provider : providerList) {
            for (GenerativeLlmModel model : provider.getSupportedModels()) {
                providers.put(model, provider);
                log.info("Registered LLM provider: {} → {} (enabled={})",
                        model, provider.getClass().getSimpleName(), model.isEnabled());
            }
        }
    }

    public GenerativeLlmProvider getProvider(GenerativeLlmModel model) {
        GenerativeLlmProvider provider = providers.get(model);
        if (provider == null) {
            throw new IllegalArgumentException("No registered provider for model: " + model);
        }
        log.info("Selected LLM provider: {} → {}", model, provider.getClass().getSimpleName());
        return provider;
    }

    // Used to know what's actually available at runtime
    public Set<GenerativeLlmModel> getAvailableModels() {
        return Collections.unmodifiableSet(providers.keySet());
    }
}
