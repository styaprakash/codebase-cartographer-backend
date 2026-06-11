package com.codebasecartographer.api.dto;

import com.codebasecartographer.api.enums.ChunkType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ASTChunk {
    private String content;
    private Integer startLine;
    private Integer endLine;
    private String scopeChain;
    private String entityName;
    private String chunkType;

    public static ChunkType mapChunkType(String type) {
        if (type == null) return ChunkType.UNKNOWN;
        return switch (type.toUpperCase()) {
            case "FUNCTION", "METHOD" -> ChunkType.FUNCTION;
            case "CLASS" -> ChunkType.CLASS;
            case "MODULE" -> ChunkType.MODULE;
            default -> ChunkType.UNKNOWN;
        };
    }
}
