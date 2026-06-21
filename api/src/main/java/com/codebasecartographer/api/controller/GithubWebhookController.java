package com.codebasecartographer.api.controller;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/github")
public class GithubWebhookController {

    private final ObjectMapper objectMapper;

    @Value("${github.webhook.secret:defaultSecret}")
    private String webhookSecret;

    public GithubWebhookController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/push", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handlePushEvent(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        if (!isValidSignature(payload, signature)) {
            log.warn("Invalid GitHub webhook signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String ref = root.has("ref") ? root.get("ref").asText() : "";

            // Only process pushes to the default branch
            String defaultBranch = root.path("repository").path("default_branch").asText("");
            if (!ref.equals("refs/heads/" + defaultBranch)) {
                log.info("Ignored push to branch {}. Default branch is {}", ref, defaultBranch);
                return ResponseEntity.ok("Ignored: not default branch");
            }

            JsonNode commits = root.path("commits");
            List<String> added = new ArrayList<>();
            List<String> modified = new ArrayList<>();
            List<String> removed = new ArrayList<>();

            if (commits.isArray()) {
                for (JsonNode commit : commits) {
                    commit.path("added").forEach(node -> added.add(node.asText()));
                    commit.path("modified").forEach(node -> modified.add(node.asText()));
                    commit.path("removed").forEach(node -> removed.add(node.asText()));
                }
            }

            String repoFullName = root.path("repository").path("full_name").asText("");

            log.info("Webhook received for {}. Added: {}, Modified: {}, Removed: {}", 
                     repoFullName, added.size(), modified.size(), removed.size());

            // TODO: Extract logic to an IncrementalIndexingService to handle partial updates
            // using the 'added', 'modified', and 'removed' arrays.

            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("Error processing webhook payload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing payload");
        }
    }

    private boolean isValidSignature(String payload, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }

        try {
            String actualSignature = signature.substring(7);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return java.security.MessageDigest.isEqual(hexString.toString().getBytes(), actualSignature.getBytes());
        } catch (Exception e) {
            log.error("Signature verification algorithm failed", e);
            return false;
        }
    }
}
