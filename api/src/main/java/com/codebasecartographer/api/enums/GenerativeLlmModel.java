package com.codebasecartographer.api.enums;

/**
 * Single source of truth for all supported generative LLM models.
 * Each constant carries the model tag sent to the inference API,
 * the provider type for factory routing, and an enabled flag.
 */
public enum GenerativeLlmModel {
    // Ollama-hosted local models
    OLLAMA_LLAMA3("llama3:8b", "ollama", true),
    OLLAMA_LLAMA3_1("llama3.1", "ollama", true),
    OLLAMA_QWEN2_5("qwen2.5:7b-instruct", "ollama", true),
    OLLAMA_DEEPSEEK_CODER("deepseek-coder:6.7b", "ollama", true),

    // Premium remote APIs
    OPENAI_GPT4("gpt-4o", "openai", true),
    DEEPSEEK_V4("deepseek-chat", "deepseek", true),
    GEMINI_1_5_FLASH("gemini-1.5-flash", "gemini", true);

    private final String modelTag;      // identifier sent to inference API
    private final String providerType;  // "ollama" | "openai" | "deepseek"
    private final boolean enabled;

    GenerativeLlmModel(String modelTag, String providerType, boolean enabled) {
        this.modelTag = modelTag;
        this.providerType = providerType;
        this.enabled = enabled;
    }

    public String getModelTag() {
        return modelTag;
    }

    public String getProviderType() {
        return providerType;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
