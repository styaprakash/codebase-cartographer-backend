package com.codebasecartographer.api.dto.request;

public record ChatRequest(
    String query,
    String llmProvider // e.g., "OLLAMA_LLAMA3"
) {}
