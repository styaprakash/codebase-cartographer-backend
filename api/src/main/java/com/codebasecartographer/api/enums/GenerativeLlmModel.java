package com.codebasecartographer.api.enums;

/**
 * Single source of truth for all supported generative LLM models.
 * Each constant carries the model tag sent to the inference API,
 * the provider type for factory routing, and an enabled flag.
 */
public enum GenerativeLlmModel {
    // ── Ollama-hosted local models ────────────────────────────────
    OLLAMA_LLAMA3("llama3:8b", "ollama", true),
    OLLAMA_LLAMA3_1("llama3.1", "ollama", true),
    OLLAMA_QWEN2_5("qwen2.5:7b-instruct", "ollama", true),
    OLLAMA_DEEPSEEK_CODER("deepseek-coder:6.7b", "ollama", true),
    OLLAMA_GEMMA_4_E4B("gemma4:e4b", "ollama", true),

    // ── Google AI Studio (Bleeding Edge) ──────────────────────────
    GEMINI_1_5_FLASH("gemini-1.5-flash", "gemini", true),
    GEMINI_3_5_FLASH("gemini-3.5-flash", "gemini", true),
    GEMINI_2_5_FLASH("gemini-2.5-flash", "gemini", true),
    GEMINI_2_0_FLASH("gemini-2.0-flash", "gemini", true),
    GEMINI_2_5_FLASH_LITE("gemini-2.5-flash-lite", "gemini", true),
    GEMINI_3_1_FLASH_LITE_PREVIEW("gemini-3.1-flash-lite-preview", "gemini", true),

    // ── Nvidia API ────────────────────────────────────────────────
    NVIDIA_GLM_5_2("glm-5.2", "nvidia", true),

    // ── Premium remote APIs ───────────────────────────────────────
    OPENAI_GPT4("gpt-4o", "openai", true),
    DEEPSEEK_V4("deepseek-chat", "deepseek", true),

    // ── OpenRouter models (single API key, access to many models) ─
    OPENROUTER_DEEPSEEK_V4_PRO("deepseek/deepseek-v4-pro", "openrouter", true),
    OPENROUTER_DEEPSEEK_V4_FLASH("deepseek/deepseek-v4-flash", "openrouter", true),
    OPENROUTER_GLM_5_2("z-ai/glm-5.2", "openrouter", true),
    OPENROUTER_QWEN_3("qwen/qwen3-235b-a22b", "openrouter", true);

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
