// What the client sends to trigger indexing on a repository.
package com.codebasecartographer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexRequest {
    @NotBlank(message="Github Repository ID is required")
    private String githubRepoId;

    @NotBlank(message = "Repository name is required")
    private String name;

    @NotBlank(message = "Full repository name is required")
    private String fullName;

    @NotBlank(message = "Branch name is required")
    private String branch;

    private String language;
}
