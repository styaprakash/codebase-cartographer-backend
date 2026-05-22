package com.codebasecartographer.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    // JWT token — frontend stores this and sends it on every request
    private String token;

    // User info — frontend uses this to show name/email immediately
    // without making a second GET /api/me call
    private UserResponse user;
}
