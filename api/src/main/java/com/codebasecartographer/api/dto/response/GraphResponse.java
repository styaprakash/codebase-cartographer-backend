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
    private List<GaphNode> node = new ArrayList<>();

    @Builder.Default
    private List<GraphEdge> edge = new ArrayList<>();
    
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GaphNode{
        private String id;     // e.g., "com.codebasecartographer.api.entity.User"
        private String label;  // e.g., "User.java"
        private String type;   // e.g., "CLASS", "INTERFACE", "CONTROLLER"
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEdge{
        private String source; // e.g., "UserController" (matches a GraphNode id)
        private String target; // e.g., "UserService"   (matches a GraphNode id)
    }
}