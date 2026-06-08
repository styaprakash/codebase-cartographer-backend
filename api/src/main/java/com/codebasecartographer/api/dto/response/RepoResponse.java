package com.codebasecartographer.api.dto.response;

import java.time.LocalDateTime;

import com.codebasecartographer.api.enums.RepositoryStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoResponse {
    private String id;
    private String userId;
    private String githubRepoId;
    private String name;
    private String fullName;
    private String branch;
    private String language;
    private com.codebasecartographer.api.enums.EmbeddingModel embeddingModel;
    private RepositoryStatus status;
    private Integer totalFiles;
    private Integer indexedFiles;
    private String errorMessage;
    private LocalDateTime indexedAt;
    private LocalDateTime createdAt;
}
