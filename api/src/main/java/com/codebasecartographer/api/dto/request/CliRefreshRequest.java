package com.codebasecartographer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/auth/cli/refresh
 * The CLI sends its current refresh token to obtain a new
 * access token and a rotated refresh token.
 */
@Data
public class CliRefreshRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
