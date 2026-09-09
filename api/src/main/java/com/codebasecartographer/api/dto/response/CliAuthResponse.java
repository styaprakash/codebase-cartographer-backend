package com.codebasecartographer.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for CLI authentication endpoints (exchange and refresh).
 * Contains an access token (JWT) for gRPC auth and a refresh token
 * for obtaining new access tokens without re-authenticating.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CliAuthResponse {

    // Short-lived JWT — used in gRPC metadata for authentication
    private String accessToken;

    // Long-lived opaque token — stored in OS keychain, used to refresh access tokens
    private String refreshToken;

    // Access token lifetime in seconds (so CLI knows when to refresh)
    private long expiresIn;

    // User info for CLI display
    private String userId;
    private String name;
}
