package com.codebasecartographer.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codebasecartographer.api.dto.request.ChatRequest;
import com.codebasecartographer.api.dto.response.ChatResponse;
import com.codebasecartographer.api.service.LlmGenerationService;

import com.codebasecartographer.api.service.QueryService;
import com.codebasecartographer.api.service.UserService;
import com.codebasecartographer.api.exception.RateLimitException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController extends BaseController {
    private final LlmGenerationService llmGenerationService;
    private final QueryService queryService;
    private final UserService userService;

    @PostMapping("/{repoId}")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable String repoId,
            @RequestBody ChatRequest request) {
        log.info("Received chat request for repo: {} with provider: {}", repoId, request.llmProvider());
        
        String userId = getCurrentUserId();
        
        // 1. Check daily query limit
        if (userService.isQueryLimitReached(userId)) {
            log.warn("Chat rate limit reached for user {}", userId);
            throw new RateLimitException(userId, 20); // 20 queries per day limit
        }

        // 2. Generate response using LLM
        ChatResponse response = llmGenerationService.generateChatResponse(repoId, request);
        
        // 3. Save to database for history
        queryService.saveQueryLog(userId, repoId, request.query(), response.answer(), 0);
        
        // 4. Increment daily count
        userService.incrementQueryCount(userId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/models")
    public ResponseEntity<Set<String>> getAvailableModels() {
        // Return only the models that are fully configured and ready (have API keys loaded)
        Set<String> activeModels = llmGenerationService.getAvailableModels().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
        return ResponseEntity.ok(activeModels);
    }
}
