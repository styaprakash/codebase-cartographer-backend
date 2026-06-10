package com.codebasecartographer.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private String chunkId;
    private String content;
    private String filePath;
    private Integer startLine;
    private Integer endLine;
    private Double similarity;
}
