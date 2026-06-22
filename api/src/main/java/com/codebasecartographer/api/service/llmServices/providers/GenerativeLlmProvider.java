package com.codebasecartographer.api.service.llmServices.providers;

import java.util.Set;

import com.codebasecartographer.api.enums.GenerativeLlmModel;

/**
 * Contract for all generative LLM providers.
 * A provider may serve one or more GenerativeLlmModel enums
 * (e.g. the Ollama provider handles all locally-hosted models).
 */
public interface GenerativeLlmProvider {
    // Which enum constants this provider serves
    Set<GenerativeLlmModel> getSupportedModels();

    // Generate a response using the specified model
    String generateResponse(GenerativeLlmModel model, String prompt);
}
