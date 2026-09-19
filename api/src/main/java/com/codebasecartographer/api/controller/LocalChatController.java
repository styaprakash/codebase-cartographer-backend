package com.codebasecartographer.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codebasecartographer.api.dto.request.LocalChatRequest;
import com.codebasecartographer.api.dto.response.LocalChatResponse;
import com.codebasecartographer.api.entity.LocalJob;
import com.codebasecartographer.api.service.LocalJobService;

import jakarta.validation.Valid;

@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowedHeaders = "*", allowCredentials = "true")
@RestController
@RequestMapping("/api/local/chat")
public class LocalChatController extends BaseController {

    private final LocalJobService localJobService;

    public LocalChatController(LocalJobService localJobService) {
        this.localJobService = localJobService;
    }

    @PostMapping
    public ResponseEntity<LocalChatResponse> submitJob(@Valid @RequestBody LocalChatRequest request) {
        String userId = getCurrentUserId();
        
        LocalJob job = localJobService.submitJob(userId, request.getModel(), request.getPrompt());
        
        LocalChatResponse response = LocalChatResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus().name())
                .build();
                
        return ResponseEntity.ok(response);
    }
}
