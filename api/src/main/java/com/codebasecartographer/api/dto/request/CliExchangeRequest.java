package com.codebasecartographer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/auth/cli/exchange
 * The CLI sends its GitHub access token (obtained via Device Flow)
 * and a human-readable device name.
 */
@Data
public class CliExchangeRequest {

    @NotBlank(message = "GitHub access token is required")
    private String githubAccessToken;

    @NotBlank(message = "Device name is required")
    private String deviceName;
}
