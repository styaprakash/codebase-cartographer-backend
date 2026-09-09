package com.codebasecartographer.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.codebasecartographer.api.dto.response.CliAuthResponse;
import com.codebasecartographer.api.dto.response.UserResponse;
import com.codebasecartographer.api.entity.CliSession;
import com.codebasecartographer.api.repository.CliSessionRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles CLI authentication:
 * - Token exchange: GitHub access token → application credentials
 * - Token refresh: rotate refresh token, issue new access token
 * - Session management: list and revoke CLI sessions
 */
@Slf4j
@Service
public class CliAuthService {

    private static final long REFRESH_TOKEN_VALIDITY_DAYS = 90;
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final WebClient webClient;
    private final UserService userService;
    private final JwtService jwtService;
    private final CliSessionRepository cliSessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public CliAuthService(UserService userService, JwtService jwtService,
                          CliSessionRepository cliSessionRepository) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
        this.userService = userService;
        this.jwtService = jwtService;
        this.cliSessionRepository = cliSessionRepository;
    }

    // ── Exchange GitHub token for application credentials ─────────

    /**
     * Verifies a GitHub access token by calling the GitHub API directly,
     * maps the identity to a Codebase Cartographer user, and issues
     * an access token + refresh token pair.
     */
    @Transactional
    public CliAuthResponse exchangeGithubToken(String githubAccessToken, String deviceName) {
        // 1. Verify the GitHub token and get the user profile
        Map<String, Object> githubProfile = fetchGithubProfile(githubAccessToken);

        String githubId = String.valueOf(githubProfile.get("id"));
        String name = (String) githubProfile.get("name");
        if (name == null || name.isBlank()) {
            name = (String) githubProfile.get("login");
        }
        String email = (String) githubProfile.get("email");
        if (email == null || email.isBlank()) {
            email = fetchGithubPrimaryEmail(githubAccessToken);
        }

        log.info("CLI token exchange for GitHub user: {} ({})", githubId, name);

        // 2. Find or create the application user (same identity as website)
        UserResponse user = userService.findOrCreateUserFromGithubProfile(githubId, name, email);

        // 3. Generate application credentials
        String accessToken = jwtService.generateCliToken(user.getId());
        String refreshToken = generateRefreshToken();
        String tokenHash = hashToken(refreshToken);

        // 4. Persist the session
        CliSession session = CliSession.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .deviceName(deviceName)
                .expiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_VALIDITY_DAYS))
                .build();
        cliSessionRepository.save(session);

        log.info("CLI session created for user {} on device '{}'", user.getId(), deviceName);

        return CliAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(3600) // 1 hour in seconds
                .userId(user.getId())
                .name(user.getName())
                .build();
    }

    // ── Refresh access token ─────────────────────────────────────

    /**
     * Validates the refresh token, rotates it, and issues a new access token.
     * The old refresh token is invalidated immediately (rotation).
     */
    @Transactional
    public CliAuthResponse refreshAccessToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        CliSession session = cliSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("CLI refresh attempt with unknown token");
                    return new SecurityException("Invalid refresh token");
                });

        // Check revocation
        if (session.isRevoked()) {
            log.warn("CLI refresh attempt with revoked token for user {}", session.getUserId());
            throw new SecurityException("Refresh token has been revoked");
        }

        // Check expiration
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("CLI refresh attempt with expired token for user {}", session.getUserId());
            throw new SecurityException("Refresh token has expired");
        }

        // Rotate: generate new refresh token, update the session
        String newRefreshToken = generateRefreshToken();
        String newTokenHash = hashToken(newRefreshToken);
        session.setTokenHash(newTokenHash);
        session.setLastUsedAt(LocalDateTime.now());
        cliSessionRepository.save(session);

        // Issue new access token
        String accessToken = jwtService.generateCliToken(session.getUserId());

        log.info("CLI token refreshed for user {} on device '{}'",
                session.getUserId(), session.getDeviceName());

        return CliAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(3600)
                .userId(session.getUserId())
                .build();
    }

    // ── Session management ───────────────────────────────────────

    public List<CliSession> getUserSessions(String userId) {
        return cliSessionRepository.findByUserIdAndRevokedFalse(userId);
    }

    @Transactional
    public void revokeSession(String userId, String sessionId) {
        CliSession session = cliSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SecurityException("Session not found"));
        session.setRevoked(true);
        cliSessionRepository.save(session);
        log.info("CLI session {} revoked for user {}", sessionId, userId);
    }

    // ── GitHub API calls ─────────────────────────────────────────

    private Map<String, Object> fetchGithubProfile(String githubAccessToken) {
        try {
            return webClient.get()
                    .uri("/user")
                    .header("Authorization", "Bearer " + githubAccessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (WebClientResponseException.Unauthorized e) {
            throw new SecurityException("Invalid GitHub access token");
        } catch (Exception e) {
            log.error("Failed to call GitHub API: {}", e.getMessage());
            throw new RuntimeException("GitHub API call failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchGithubPrimaryEmail(String githubAccessToken) {
        try {
            List<Map<String, Object>> emails = webClient.get()
                    .uri("/user/emails")
                    .header("Authorization", "Bearer " + githubAccessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (emails != null) {
                return emails.stream()
                        .filter(e -> Boolean.TRUE.equals(e.get("primary")))
                        .map(e -> (String) e.get("email"))
                        .findFirst()
                        .orElse(emails.isEmpty() ? null : (String) emails.get(0).get("email"));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch GitHub emails: {}", e.getMessage());
        }
        return null;
    }

    // ── Token utilities ──────────────────────────────────────────

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return "crt_" + HexFormat.of().formatHex(bytes);
    }

    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
