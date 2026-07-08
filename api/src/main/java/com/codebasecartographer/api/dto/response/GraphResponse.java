package com.codebasecartographer.api.dto.response;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphResponse {
    @Builder.Default
    private List<GraphNode> nodes = new ArrayList<>();

    @Builder.Default
    private List<GraphEdge> edges = new ArrayList<>();
    
    // ── Inner class: GraphNode ─────────────────────────────────────
    // Represents one file in the dependency graph
    // React Flow uses these to render bubbles/boxes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphNode{
        private String id;        // e.g., "src/components/App.tsx"
        private String label;     // e.g., "App.tsx"
        private String category;  // e.g., "component", "service", "utility", "config", "folder"
        private String filePath;  // full path, same as id for files
    }
    
    // ── Inner class: GraphEdge ─────────────────────────────────────
    // Represents an import relationship between two files
    // React Flow draws a line between source and target
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEdge{
        private String id;      // unique edge id e.g. "login.ts→auth.ts"
        private String source; // e.g., "UserController" (matches a GraphNode id)
        private String target; // e.g., "UserService"   (matches a GraphNode id)
    }
}