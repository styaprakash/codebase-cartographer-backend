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
                if (provider.isEnabled()) {
                    providers.put(model, provider);
                    log.info("Registered LLM provider: {} → {} (enabled=true)",
                            model, provider.getClass().getSimpleName());
                } else {
                    log.warn("Skipped disabled LLM provider: {} → {} (enabled=false)",
                            model, provider.getClass().getSimpleName());
                }
            }
        }

        if (providers.isEmpty()) {
            log.error("No LLM providers available. Configure at least one of: "
                    + "OPENAI_API_KEY, OPENROUTER_API_KEY, GEMINI_API_KEY, DEEPSEEK_API_KEY, "
                    + "NVIDIA_API_KEY, or start Ollama on port 11434");
        } else {
            log.info("Enabled LLM providers: {}", providers.keySet());
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
