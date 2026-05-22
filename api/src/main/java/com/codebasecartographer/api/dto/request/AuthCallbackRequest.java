package com.codebasecartographer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthCallbackRequest {
    //Github unique user ID
    @NotBlank(message="githubID is required!")
    private String githubId;


    @NotBlank(message="name is required!")
    private String name;

    @NotBlank(message="email is required!")
    private String email;

    //Github Oauth access token
    @NotBlank(message="access token is required!")
    private String accessToken;
}
