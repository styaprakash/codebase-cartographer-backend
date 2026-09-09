package com.codebasecartographer.api.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codebasecartographer.api.dto.request.CliExchangeRequest;
import com.codebasecartographer.api.dto.request.CliRefreshRequest;
import com.codebasecartographer.api.dto.response.CliAuthResponse;
import com.codebasecartographer.api.entity.CliSession;
import com.codebasecartographer.api.service.CliAuthService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * REST endpoints for CLI authentication.
 *
 * Exchange and refresh are public (under /api/auth/cli/**)
 * because the CLI doesn't have a token yet when calling them.
 *
 * Session management endpoints require an authenticated user.
 */
@Slf4j
@RestController
public class CliAuthController {

    private final CliAuthService cliAuthService;

    public CliAuthController(CliAuthService cliAuthService) {
        this.cliAuthService = cliAuthService;
    }

    // ── Public endpoints (no auth required) ──────────────────────

    /**
     * POST /api/auth/cli/exchange
     *
     * The CLI calls this after completing GitHub Device Flow.
     * Sends its GitHub access token, receives application credentials.
     */
    @PostMapping("/api/auth/cli/exchange")
    public ResponseEntity<CliAuthResponse> exchange(@Valid @RequestBody CliExchangeRequest request) {
        log.info("CLI token exchange request from device: {}", request.getDeviceName());
        CliAuthResponse response = cliAuthService.exchangeGithubToken(
                request.getGithubAccessToken(),
                request.getDeviceName()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/cli/refresh
     *
     * The CLI calls this when its access token expires.
     * Sends current refresh token, receives new access + refresh tokens.
     */
    @PostMapping("/api/auth/cli/refresh")
    public ResponseEntity<CliAuthResponse> refresh(@Valid @RequestBody CliRefreshRequest request) {
        CliAuthResponse response = cliAuthService.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    // ── Authenticated endpoints (session management) ─────────────

    /**
     * GET /api/cli/sessions
     *
     * Lists the authenticated user's active CLI sessions.
     * Used by the frontend to show "Manage Devices" UI.
     */
    @GetMapping("/api/cli/sessions")
    public ResponseEntity<List<CliSession>> listSessions(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        List<CliSession> sessions = cliAuthService.getUserSessions(userId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * DELETE /api/cli/sessions/{sessionId}
     *
     * Revokes a specific CLI session. The refresh token becomes invalid
     * immediately. The CLI will fail on its next refresh attempt.
     */
    @DeleteMapping("/api/cli/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId,
                                               Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        cliAuthService.revokeSession(userId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
