package com.codebasecartographer.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codebasecartographer.api.dto.request.ChatRequest;
import com.codebasecartographer.api.dto.response.ChatResponse;
import com.codebasecartographer.api.service.LlmGenerationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final LlmGenerationService llmGenerationService;

    @PostMapping("/{repoId}")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable String repoId,
            @RequestBody ChatRequest request) {
        log.info("Received chat request for repo: {} with provider: {}", repoId, request.llmProvider());
        
        ChatResponse response = llmGenerationService.generateChatResponse(repoId, request);
        return ResponseEntity.ok(response);
    }
}
