package com.codebasecartographer.api.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.codebasecartographer.api.dto.response.GraphResponse;
import com.codebasecartographer.api.dto.response.GraphResponse.GraphEdge;
import com.codebasecartographer.api.dto.response.GraphResponse.GraphNode;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.exception.BadRequestException;
import com.codebasecartographer.api.exception.ResourceNotFoundException;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;
import com.codebasecartographer.api.utils.FileUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GraphService {

    private final CodeChunkRepository codeChunkRepository;
    private final RepositoryRepository repositoryRepository;

    public GraphService(CodeChunkRepository codeChunkRepository,
                        RepositoryRepository repositoryRepository) {
        this.codeChunkRepository = codeChunkRepository;
        this.repositoryRepository = repositoryRepository;
    }

    // ── Main Method: getGraph ─────────────────────────────────────
    public GraphResponse getGraph(String userId, String repoId) {

        Repository repo = verifyRepoAccess(userId, repoId);

        if (repo.getStatus() != RepositoryStatus.INDEXED) {
            log.warn("Graph requested for non-indexed repo {} (status: {}) by user {}", repoId, repo.getStatus(), userId);
            throw new BadRequestException(
                "Repository must be fully indexed before viewing dependency graph. " +
                "Current status: " + repo.getStatus());
        }

        try {
            // Use native query to avoid pgvector JDBC driver crash (PSQLException in TypeInfoCache)
            List<String> filePaths = codeChunkRepository.findDistinctFilePathsByRepoId(repoId);

            if (filePaths.isEmpty()) {
                return GraphResponse.builder().nodes(List.of()).edges(List.of()).build();
            }

            List<GraphNode> nodes = buildNodes(filePaths);
            List<GraphEdge> edges = buildEdges(filePaths);

            return GraphResponse.builder()
                    .nodes(nodes)
                    .edges(edges)
                    .build();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to build graph for repo {}: {}", repoId, e.getMessage(), e);
            return GraphResponse.builder().nodes(List.of()).edges(List.of()).build();
        }
    }

    // ── Build Nodes ───────────────────────────────────────────────
    // V1: File System Hierarchy Graph
    // Creates folder nodes + file nodes from flat file paths
    private List<GraphNode> buildNodes(List<String> filePaths) {

        // Collect all unique paths: parent folders + files
        Set<String> allPaths = new LinkedHashSet<>();

        for (String filePath : filePaths) {
            // Add all parent directory segments
            String[] parts = filePath.split("/");
            StringBuilder currentPath = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (currentPath.length() > 0) {
                    currentPath.append("/");
                }
                currentPath.append(parts[i]);
                allPaths.add(currentPath.toString());
            }
            // Add the file itself
            allPaths.add(filePath);
        }

        // Convert each path into a GraphNode
        return allPaths.stream()
                .map(path -> {
                    boolean isFile = isFilePath(path);
                    String label = isFile
                            ? FileUtils.extractFileName(path)
                            : extractFolderName(path);
                    String category = isFile
                            ? determineFileType(path)
                            : "folder";

                    return GraphNode.builder()
                            .id(path)
                            .label(label)
                            .category(category)
                            .filePath(path)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── Build Edges ───────────────────────────────────────────────
    // V1: Directory hierarchy edges (parent folder → child folder/file)
    private List<GraphEdge> buildEdges(List<String> filePaths) {

        Set<String> edgeKeys = new LinkedHashSet<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (String filePath : filePaths) {
            // Walk up the path to create parent → child edges
            String[] parts = filePath.split("/");
            StringBuilder parentPath = new StringBuilder();

            for (int i = 0; i < parts.length - 1; i++) {
                String segment = parts[i];
                String childPath;

                if (parentPath.length() > 0) {
                    parentPath.append("/");
                    parentPath.append(segment);
                    childPath = parentPath.toString();
                } else {
                    childPath = segment;
                    parentPath = new StringBuilder(segment);
                }

                // Determine the target: if this is the last segment before the file,
                // target is the file itself; otherwise target is the intermediate folder
                String target;
                if (i == parts.length - 2) {
                    // Last parent segment → target is the file
                    target = filePath;
                } else {
                    // Intermediate folder → target is the next folder level
                    target = childPath;
                }

                String source = (i == 0) ? segment : parentPath.toString();
                String edgeKey = source + "->" + target;

                if (edgeKeys.add(edgeKey)) {
                    edges.add(GraphEdge.builder()
                            .id(edgeKey)
                            .source(source)
                            .target(target)
                            .build());
                }
            }
        }

        return edges;
    }

    // ── Determine File Type ───────────────────────────────────────
    // Path-based heuristic for file category (no entity loading needed)
    private String determineFileType(String filePath) {

        String path = filePath.toLowerCase();

        if (path.contains("/component") ||
            path.contains("/components") ||
            path.contains("/view") ||
            path.contains("/views") ||
            path.contains("/page") ||
            path.contains("/pages")) {
            return "component";
        }

        if (path.contains("/service") ||
            path.contains("/services") ||
            path.contains("/api") ||
            path.contains("/controller") ||
            path.contains("/repository")) {
            return "service";
        }

        if (path.contains("/util") ||
            path.contains("/utils") ||
            path.contains("/helper") ||
            path.contains("/helpers") ||
            path.contains("/common")) {
            return "utility";
        }

        if (path.contains("config") ||
            path.contains(".config.") ||
            path.endsWith(".yaml") ||
            path.endsWith(".yml") ||
            path.endsWith(".properties") ||
            path.endsWith(".env")) {
            return "config";
        }

        return "utility";
    }

    // ── Helpers ───────────────────────────────────────────────────

    private boolean isFilePath(String path) {
        int lastSlash = path.lastIndexOf('/');
        String name = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
        return name.contains(".");
    }

    private String extractFolderName(String folderPath) {
        int lastSlash = folderPath.lastIndexOf('/');
        return (lastSlash >= 0) ? folderPath.substring(lastSlash + 1) : folderPath;
    }

    // ── getFilePaths ──────────────────────────────────────────────
    public List<String> getFilePaths(String userId, String repoId) {
        verifyRepoAccess(userId, repoId);
        return codeChunkRepository.findDistinctFilePathsByRepoId(repoId);
    }

    // ── Verify Repo Access ────────────────────────────────────────
    private Repository verifyRepoAccess(String userId, String repoId) {
        return repositoryRepository
                .findByUserIdAndId(userId, repoId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Repository", "id", repoId));
    }
}
