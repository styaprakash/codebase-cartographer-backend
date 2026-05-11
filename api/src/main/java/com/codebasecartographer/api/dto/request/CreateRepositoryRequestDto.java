package com.codebasecartographer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRepositoryRequestDto {
    @NotBlank(message= "Github repository ID is required!")
    private String githubRepoId;

    @NotBlank(message = "Repository name is required!")
    private String name;

    @NotBlank(message = "Full Repository name is required!")
    private String fullName;

    @NotBlank(message = "Branch name is required!")
    private String branch;

    private String language;

}
