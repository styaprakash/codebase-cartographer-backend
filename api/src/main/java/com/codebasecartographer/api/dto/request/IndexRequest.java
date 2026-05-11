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

    @Builder.Default
    private String branch = "main";
}
