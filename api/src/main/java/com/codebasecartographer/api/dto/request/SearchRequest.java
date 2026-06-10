package com.codebasecartographer.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {
    private String query;
    @Builder.Default
    private Double threshold = 0.7;
    @Builder.Default
    private Integer limit = 10;
}
