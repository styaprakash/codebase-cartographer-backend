package com.codebasecartographer.api.enums;

/**
 * Single source of truth for all supported embedding models.
 * Each constant carries the metadata needed to configure a provider.
 * Only models with enabled=true will be considered for new repositories.
 */
public enum EmbeddingModel {
    // Currently active — the only model pulled in Ollama
    QWEN3_EMBEDDING("qwen3-embedding:8b", 1536, "ollama", true),

    // Placeholders — enable when model is pulled and provider is implemented
    EMBEDDING_GEMMA("embedding-gemma:300m", 768, "ollama", false),
    NV_EMBED_V2("nv-embed-v2", 4096, "ollama", false),
    GEMINI_EMBEDDING("gemini-embedding", 768, "gemini", false);

    private final String modelTag;      // identifier sent to inference API
    private final int dimension;        // vector dimension produced
    private final String providerType;  // "ollama" | "gemini" | "openai"
    private final boolean enabled;      // available for new repository assignment

    EmbeddingModel(String modelTag, int dimension, String providerType, boolean enabled) {
        this.modelTag = modelTag;
        this.dimension = dimension;
        this.providerType = providerType;
        this.enabled = enabled;
    }

    public String getModelTag() { return modelTag; }
    public int getDimension() { return dimension; }
    public String getProviderType() { return providerType; }
    public boolean isEnabled() { return enabled; }
}
