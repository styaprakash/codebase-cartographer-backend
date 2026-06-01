package com.codebasecartographer.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.codebasecartographer.api.entity.Repository;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class GitHubFileService {
    private final WebClient webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer only for Trees call
                .build();
    
    //Supported file extensions for parsing
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".java", ".ts", ".tsx", ".js", ".jsx",
        ".py", ".go", ".rs", ".cpp", ".c"
    );

    // Fetch all file paths from Github Tress API----------------->
    // Github Tress API returns the full file tree recursively
    // One api call gets all file paths
    public List<GithubFile> fetchRepoFiles(Repository repo, String accessToken){
        String[] parts = repo.getFullName().split("/");
        String owner = parts[0];
        String repoName = parts[1];
        String branch = repo.getBranch();

        // GitHub Trees API — recursive=1 gets entire tree in one call
        JsonNode tree = webClient.get()
            .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1",owner, repoName, branch)
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        List<GithubFile> files = new ArrayList<>();
        if(tree == null || !tree.has("tree")) return files;

        for(JsonNode item : tree.get("tree")) {
            String type = item.get("type").asText();
            String path = item.get("path").asText();

            // Only process files (not directories) with supported extensions
            if (!"blob".equals(type)) continue;
            if (!isSupportedFile(path))   continue;

            //Fetch the actual file content
            String content = fetchFileContent(owner, repoName, path, branch, accessToken);

            if(content !=null && !content.isBlank()) {
                files.add(new GithubFile(path, content));
            }
        }

        return files;
    }

    //Fetch individual file content---------------------->
    private String fetchFileContent(String owner, String repo, String path, String branch,String token){
        try{
            JsonNode response = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}",
                            owner, repo, path, branch)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if(response == null || !response.has("content")) return null;

            //Github returns content as Base64 — decode it
            String encoded = response.get("content").asText()
                            .replaceAll("\\s", ""); //remove newlines from base64

            return new String(java.util.Base64.getDecoder().decode(encoded));

        }catch(Exception e){
            return null; // Skip files that can't be fetched (deleted, too large etc.)
        }
    }

    //Check if the file extension is supported------------------------->
    private boolean isSupportedFile(String path) {
        return SUPPORTED_EXTENSIONS.stream()
            .anyMatch(path::endsWith);
    }

    //Simple record to hold file data--------------------->
    public record GithubFile(String path, String content) {}
}
