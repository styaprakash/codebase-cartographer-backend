package com.codebasecartographer.api.controller;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.codebasecartographer.api.dto.request.IndexRequest;
import com.codebasecartographer.api.dto.response.RepoResponse;
import com.codebasecartographer.api.service.IndexingService;
import com.codebasecartographer.api.service.RepoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class RepoController {
    // Two services needed
    private final RepoService repoService;
    private final IndexingService indexingService;

    //Constructor injection
    public RepoController(RepoService repoService, IndexingService indexingService){
        this.repoService = repoService;
        this.indexingService = indexingService;
    }

    // GET  /api/repos
    @GetMapping("/repos")
    public List<RepoResponse> getRepos(@RequestHeader("X-User-Id") String userId){
        // Call service to get list of repos for the user
        List<RepoResponse> repos = repoService.getAllRepos(userId);

        // Return list of repos (200 OK)
        return repos;
    }   

    // GET  /api/repos/{id}
    @GetMapping("/repos/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public RepoResponse getRepoById(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("id") String id
    ){
        return repoService.getRepoById(userId, id);
    }

    // POST /api/repos
    @PostMapping("/repos")
    @ResponseStatus(HttpStatus.CREATED)
    public RepoResponse createRepo(
        @RequestHeader("X-User-Id") String userId, 
        @Valid @RequestBody IndexRequest request){
        return repoService.createRepo(userId, 
            request.getGithubRepoId(),
            request.getName(),
            request.getFullName(), 
            request.getBranch(),
            request.getLanguage()
        );
    }

    // POST /api/repos/{id}/index
    @PostMapping("/repos/{id}/index")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RepoResponse triggerIndexing(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("id") String id
    ){
        return indexingService.triggerIndexing(id);
    }

    // GET  /api/repos/{id}/status
    @GetMapping("repos/{id}/status")
    public RepoResponse getIndexingStatus(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("id") String id
    ){
        return indexingService.getIndexingStatus(id);
    }
}
