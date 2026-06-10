package com.codebasecartographer.api.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codebasecartographer.api.dto.request.SearchRequest;
import com.codebasecartographer.api.dto.response.SearchResult;
import com.codebasecartographer.api.service.SemanticSearchService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/repos")
public class SemanticSearchController {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchController.class);

    private final SemanticSearchService searchService;

    public SemanticSearchController(SemanticSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/{repoId}/search")
    public ResponseEntity<List<SearchResult>> search(
            @PathVariable String repoId,
            @Valid @RequestBody SearchRequest request) {

        log.info("POST /api/repos/{}/search - query: {}", repoId, request.getQuery());

        List<SearchResult> results = searchService.search(
                repoId,
                request.getQuery(),
                request.getThreshold(),
                request.getLimit()
        );

        return ResponseEntity.ok(results);
    }
}
