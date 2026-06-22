package com.codebasecartographer.api.service.llmServices.providers;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.enums.GenerativeLlmModel;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles ALL Ollama-hosted local models.
 * The model tag injected into the request payload is resolved dynamically
 * from the GenerativeLlmModel enum selected by the user.
 */
@Slf4j
@Component
public class OllamaLlmProvider implements GenerativeLlmProvider {
    private final RestClient restClient;

    public OllamaLlmProvider(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("http://localhost:11434")
                .build();
    }

    @Override
    public Set<GenerativeLlmModel> getSupportedModels() {
        return Set.of(
                GenerativeLlmModel.OLLAMA_LLAMA3,
                GenerativeLlmModel.OLLAMA_LLAMA3_1,
                GenerativeLlmModel.OLLAMA_QWEN2_5,
                GenerativeLlmModel.OLLAMA_DEEPSEEK_CODER
        );
    }

    @Override
    @Bulkhead(name = "local-ollama")
    public String generateResponse(GenerativeLlmModel model, String prompt) {
        log.info("Generating response using {} (tag={})", model, model.getModelTag());

        OllamaRequest request = new OllamaRequest(model.getModelTag(), prompt, false);

        OllamaResponse response = restClient.post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(OllamaResponse.class);

        if (response == null || response.response() == null) {
            throw new RuntimeException("Received null response from Ollama for model: " + model);
        }

        return response.response();
    }

    private record OllamaRequest(String model, String prompt, boolean stream) {}
    private record OllamaResponse(String response) {}
}
