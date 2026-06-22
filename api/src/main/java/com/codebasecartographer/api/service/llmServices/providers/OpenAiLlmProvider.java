package com.codebasecartographer.api.service.llmServices.providers;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.codebasecartographer.api.enums.GenerativeLlmModel;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OpenAiLlmProvider implements GenerativeLlmProvider {
    private final RestClient restClient;

    public OpenAiLlmProvider(RestClient.Builder builder, @Value("${openai.api.key:dummy}") String apiKey) {
        this.restClient = builder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public Set<GenerativeLlmModel> getSupportedModels() {
        return Set.of(GenerativeLlmModel.OPENAI_GPT4);
    }

    @Override
    @RateLimiter(name = "premium-api")
    @Retry(name = "premium-api")
    public String generateResponse(GenerativeLlmModel model, String prompt) {
        log.info("Generating response using {} (tag={})", model, model.getModelTag());

        OpenAiRequest request = new OpenAiRequest(model.getModelTag(), List.of(new Message("user", prompt)));

        OpenAiResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(OpenAiResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new RuntimeException("Received invalid response from OpenAI");
        }

        return response.choices().get(0).message().content();
    }

    private record Message(String role, String content) {}
    private record OpenAiRequest(String model, List<Message> messages) {}
    private record Choice(Message message) {}
    private record OpenAiResponse(List<Choice> choices) {}
}
