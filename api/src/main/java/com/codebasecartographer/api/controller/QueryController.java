package com.codebasecartographer.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codebasecartographer.api.dto.request.QueryRequest;
import com.codebasecartographer.api.dto.response.QueryResponse;
import com.codebasecartographer.api.service.QueryService;


@RestController
@RequestMapping("/api")
public class QueryController {
    private final QueryService queryService;

    //Constructor injection
    public QueryController(QueryService queryService){
        this.queryService = queryService;
    }

    // POST /api/repos/:id/query
    // Triggers: user hits Send button on chat
    @PostMapping("/repos/{id}/query")
    public QueryResponse addQuery(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("id") String repoId,
        @RequestBody QueryRequest request
    ){
        String question = request.getQuestion();
        return queryService.query(userId, repoId, question);
    }


    // GET /api/repos/{id}/queries
    // Triggers: Repo Explorer page loads, restores previous chat history
    @GetMapping("/repos/{id}/queries")
    public List<QueryResponse> getMethodName(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("id") String repoId
    ) {
        return queryService.getQueryHistory(userId, repoId);
    }
    
}
