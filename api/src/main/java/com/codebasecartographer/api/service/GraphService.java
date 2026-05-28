package com.codebasecartographer.api.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.codebasecartographer.api.dto.response.GraphResponse;
import com.codebasecartographer.api.dto.response.GraphResponse.GraphEdge;
import com.codebasecartographer.api.dto.response.GraphResponse.GraphNode;
import com.codebasecartographer.api.entity.CodeChunk;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.exception.BadRequestException;
import com.codebasecartographer.api.exception.ResourceNotFoundException;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GraphService {

    // Needs these two repositories:
    // CodeChunkRepository → chunks contain file paths + import info
    // RepositoryRepository → verify repo exists + ownership
    private final CodeChunkRepository codeChunkRepository;
    private final RepositoryRepository repositoryRepository;

    public GraphService(CodeChunkRepository codeChunkRepository,
                        RepositoryRepository repositoryRepository) {
        this.codeChunkRepository = codeChunkRepository;
        this.repositoryRepository = repositoryRepository;
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC METHODS
    // ═══════════════════════════════════════════════════════════════

    // ── Main Method: getGraph ─────────────────────────────────────
    // Called by GraphController on GET /api/repos/:id/graph
    // Returns nodes + edges for React Flow to render
    //
    // Flow:
    // 1. Verify repo exists + belongs to user
    // 2. Verify repo is indexed (no chunks = no graph)
    // 3. Fetch all chunks for this repo
    // 4. Build nodes from unique file paths
    // 5. Build edges from import relationships (Week 6)
    // 6. Return GraphResponse
    public GraphResponse getGraph(String userId, String repoId) {

        // Step 1 — Security: repo must exist + belong to this user
        Repository repo = verifyRepoAccess(userId, repoId);

        // Step 2 — Repo must be fully indexed
        // No chunks in DB = no graph to build
        if (repo.getStatus() != RepositoryStatus.INDEXED) {
            log.warn("Graph requested for non-indexed repo {} (status: {}) by user {}", repoId, repo.getStatus(), userId);
            throw new BadRequestException(
                "Repository must be fully indexed before viewing dependency graph. " +
                "Current status: " + repo.getStatus());
        }

        // Step 3 — Fetch ALL chunks for this repo from DB
        // Each chunk has: filePath, chunkType, chunkName, content
        List<CodeChunk> allChunks = codeChunkRepository
                .findByRepository_Id(repoId);

        // Step 4 — Build nodes (one per unique file)
        List<GraphNode> nodes = buildNodes(allChunks);

        // Step 5 — Build edges (import relationships between files)
        // TODO Week 6: parse import statements from chunk content
        List<GraphEdge> edges = buildEdges(allChunks);

        return GraphResponse.builder()
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE METHODS
    // ═══════════════════════════════════════════════════════════════

    // ── Build Nodes ───────────────────────────────────────────────
    // Creates one GraphNode per unique file in the repo
    //
    // Why unique files?
    // One file has MANY chunks (multiple functions/classes)
    // But on the graph, each FILE = one node
    // We group all chunks by filePath → one node per file
    //
    // Example:
    // chunks: [auth.ts:login(), auth.ts:logout(), payment.ts:charge()]
    // nodes:  [auth.ts node, payment.ts node]
    private List<GraphNode> buildNodes(List<CodeChunk> chunks) {

        // Group chunks by filePath
        // Map<filePath, List<CodeChunk>>
        Map<String, List<CodeChunk>> byFile = chunks.stream()
                .collect(Collectors.groupingBy(CodeChunk::getFilePath));

        // Create one node per unique file
        return byFile.entrySet().stream()
                .map(entry -> {
                    String filePath = entry.getKey();
                    List<CodeChunk> fileChunks = entry.getValue();

                    return GraphNode.builder()
                            // id = full path e.g. "src/auth/login.ts"
                            // React Flow uses this to connect edges
                            .id(filePath)

                            // label = just the filename e.g. "login.ts"
                            // Shown inside the node bubble on graph
                            .label(extractFileName(filePath))

                            // type = determines node color on React Flow graph
                            // COMPONENT → purple
                            // SERVICE   → cyan
                            // UTILITY   → green
                            // CONFIG    → amber
                            .type(determineNodeType(filePath, fileChunks))

                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── Build Edges ───────────────────────────────────────────────
    // Creates edges representing import relationships
    //
    // Example:
    // login.ts has: import { auth } from './auth'
    // Edge: login.ts → auth.ts
    //
    // React Flow draws a line between these two nodes
    //
    // Full logic requires parsing import statements from chunk content
    // That's done in Week 6 after Tree-sitter is set up
    private List<GraphEdge> buildEdges(List<CodeChunk> chunks) {

        // TODO Week 6 — parse import statements from chunk content:
        // 1. For each chunk, scan content for import lines
        //    TypeScript: import { x } from './path'
        //    Java:       import com.package.Class
        //    Python:     from module import x
        // 2. Resolve relative paths to absolute file paths
        // 3. Check if target file exists in our chunks
        // 4. Create GraphEdge(source=thisFile, target=importedFile)

        // For now — return empty list
        // Graph will show nodes only, no connecting lines
        // Week 6 fills this in
        return Collections.emptyList();
    }

    // ── Determine Node Type ───────────────────────────────────────
    // Decides the color category of each file node
    // Based on file path conventions — not AI guessing
    //
    // Why path-based detection?
    // Developers follow conventions:
    // files in /components/ → React components → COMPONENT
    // files in /services/   → business logic   → SERVICE
    // files in /utils/      → helper functions → UTILITY
    // config files          → configuration    → CONFIG
    private String determineNodeType(String filePath,
                                      List<CodeChunk> fileChunks) {

        String path = filePath.toLowerCase();

        // Check path segments for known conventions
        if (path.contains("/component") ||
            path.contains("/components") ||
            path.contains("/view") ||
            path.contains("/views") ||
            path.contains("/page") ||
            path.contains("/pages")) {
            return "COMPONENT";     // purple on graph
        }

        if (path.contains("/service") ||
            path.contains("/services") ||
            path.contains("/api") ||
            path.contains("/controller") ||
            path.contains("/repository")) {
            return "SERVICE";       // cyan on graph
        }

        if (path.contains("/util") ||
            path.contains("/utils") ||
            path.contains("/helper") ||
            path.contains("/helpers") ||
            path.contains("/common")) {
            return "UTILITY";       // green on graph
        }

        if (path.contains("config") ||
            path.contains(".config.") ||
            path.endsWith(".yaml") ||
            path.endsWith(".yml") ||
            path.endsWith(".properties") ||
            path.endsWith(".env")) {
            return "CONFIG";        // amber on graph
        }

        // Default — couldn't determine type
        return "UNKNOWN";           // gray on graph
    }

    // ── Extract File Name ─────────────────────────────────────────
    // Gets just the filename from a full path
    //
    // Example:
    // "src/auth/login.ts" → "login.ts"
    // "com/service/UserService.java" → "UserService.java"
    private String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) return filePath;
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0
                ? filePath.substring(lastSlash + 1)  // after last /
                : filePath;                            // no slash → use as-is
    }

    // Returns list of unique file paths for the file tree (left panel)
    public List<String> getFilePaths(String userId, String repoId) {

        // Security check first
        verifyRepoAccess(userId, repoId);

        // Get all chunks → extract unique file paths
        return codeChunkRepository
                .findByRepository_Id(repoId)
                .stream()
                .map(CodeChunk::getFilePath)  // get filePath from each chunk
                .distinct()                    // remove duplicates
                .sorted()                      // alphabetical order
                .collect(Collectors.toList());
    }
    // ── Verify Repo Access ────────────────────────────────────────
    // Security check reused across all public methods
    // Ensures repo exists AND belongs to this specific user
    // Prevents user A from seeing user B's dependency graph
    private Repository verifyRepoAccess(String userId, String repoId) {
        return repositoryRepository
                .findByUserIdAndId(userId, repoId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Repository", "id", repoId));
    }
}