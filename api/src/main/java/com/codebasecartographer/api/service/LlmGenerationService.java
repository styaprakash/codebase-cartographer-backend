package com.codebasecartographer.api.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.codebasecartographer.api.dto.request.ChatRequest;
import com.codebasecartographer.api.dto.response.ChatResponse;
import com.codebasecartographer.api.entity.CodeChunk;
import com.codebasecartographer.api.enums.GenerativeLlmModel;
import com.codebasecartographer.api.service.llmServices.factory.LlmProviderFactory;
import com.codebasecartographer.api.service.llmServices.providers.GenerativeLlmProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmGenerationService {
    private final CodeSearchService codeSearchService;
    private final LlmProviderFactory llmProviderFactory;

    public ChatResponse generateChatResponse(String repoId, ChatRequest request) {
        log.info("Generating chat response for repo: {}", repoId);

        // 1. Retrieve Context
        List<CodeChunk> contextChunks = codeSearchService.search(repoId, request.query(), 5);

        // 2. Build Prompt
        String prompt = buildPrompt(request.query(), contextChunks);

        // 3. Generate Response
        GenerativeLlmModel model;
        try {
            model = GenerativeLlmModel.valueOf(request.llmProvider());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid LLM Provider: " + request.llmProvider());
        }

        GenerativeLlmProvider provider = llmProviderFactory.getProvider(model);
        String answer = provider.generateResponse(model, prompt);

        // 4. Extract Citations
        List<String> citations = contextChunks.stream()
                .map(CodeChunk::getFilePath)
                .distinct()
                .collect(Collectors.toList());

        return new ChatResponse(answer, citations);
    }

    private String buildPrompt(String query, List<CodeChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert programming assistant answering questions about a specific codebase.\n");
        sb.append("Use the following context from the codebase to answer the user's question.\n");
        sb.append("If the context doesn't contain the answer, say you don't know.\n\n");
        sb.append("CONTEXT:\n");

        for (CodeChunk chunk : chunks) {
            sb.append(String.format("File: %s\n```\n%s\n```\n\n", chunk.getFilePath(), chunk.getContent()));
        }

        sb.append("USER QUESTION:\n").append(query).append("\n");
        return sb.toString();
    }
}
