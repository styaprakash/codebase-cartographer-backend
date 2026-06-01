package com.codebasecartographer.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReindexRequest {
    private String name;
    private String fullName;
    private String branch;
    private String language;
}
