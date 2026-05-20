package com.codebasecartographer.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codebasecartographer.api.dto.response.GraphResponse;
import com.codebasecartographer.api.service.GraphService;

@RestController
@RequestMapping("/api") //base
public class GraphController extends BaseController {
    private final GraphService graphService;

    //constructor injection
    public GraphController(GraphService graphService){
        this.graphService = graphService;
    }

    // GET /api/repos/{id}/graph
    // Triggers: user clicks Dependency Map tab
    @GetMapping("/repos/{id}/graph")
    public GraphResponse getDependencyMap(
        @PathVariable("id") String repoId
    ){
        String userId = getCurrentUserId();
        return graphService.getGraph(userId, repoId);
    }

    // GET /api/repos/{id}/files
    // Triggers: Repo Explorer page loads (left panel file tree)
    @GetMapping("/repos/{id}/files")
    public List<String> getFilesTree(
        @PathVariable("id") String repoId
    ){
        String userId = getCurrentUserId();
        return graphService.getFilePaths(userId, repoId);
    }

}
