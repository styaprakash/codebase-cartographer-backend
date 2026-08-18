package com.codebasecartographer.api.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.event.IndexingFileEvent;
import com.codebasecartographer.api.event.IndexingStatusEvent;
import com.codebasecartographer.api.repository.RepositoryRepository;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowedHeaders = "*", allowCredentials = "true")
@Slf4j
@RestController
@RequestMapping("/api/sse")
public class IndexingSSEController {

    //use a Thread-safe map (dictionary)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final RepositoryRepository repositoryRepository;

    public IndexingSSEController(RepositoryRepository repositoryRepository) {
        this.repositoryRepository = repositoryRepository;
    }

    @SuppressWarnings("null")
    @GetMapping(value = "/indexing/{repoId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String repoId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        
        log.info("Received SSE connection request for repo: {}", repoId);
        try {
            if (repoId == null) {
                log.error("Failed to establish SSE connection: repoId is null");
                throw new IllegalArgumentException("repoId cannot be null");
            }
            
            SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // Infinite timeout
            emitters.put(repoId, emitter);
            
            // Immediately flush connection to prevent Next.js proxy timeout
            emitter.send(SseEmitter.event().name("connected").data("Stream opened successfully"));

            // Best Practice: Check the database immediately upon connection to prevent race conditions
            // where the background job finishes before the frontend establishes the SSE connection.
            Optional<Repository> optRepo = repositoryRepository.findById(repoId);
            if (optRepo.isPresent()) {
                Repository repo = optRepo.get();
                if (repo.getStatus() == RepositoryStatus.INDEXED || repo.getStatus() == RepositoryStatus.FAILED) {
                    // Schedule sending the terminal event on a separate thread to ensure
                    // Spring MVC has time to return the emitter and write the HTTP 200 OK headers first.
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(100);
                            Map<String, Object> data = new HashMap<>();
                            data.put("status", repo.getStatus().name());
                            if (repo.getErrorMessage() != null) {
                                data.put("errorMessage", repo.getErrorMessage());
                            }
                            emitter.send(SseEmitter.event().name("status").data(data));
                            emitter.complete();
                        } catch (Exception e) {
                            log.error("Failed to send terminal event for repo {}", repoId, e);
                        }
                    });
                    return emitter;
                }
            }

            emitter.onCompletion(() -> {
                log.info("SSE connection completed for repo: {}", repoId);
                emitters.remove(repoId);
            });
            emitter.onTimeout(() -> {
                log.warn("SSE connection timed out for repo: {}", repoId);
                emitter.complete();
                emitters.remove(repoId);
            });
            emitter.onError((e) -> {
                // When a client disconnects, Spring's SseEmitter internally calls completeWithError()
                // which dispatches to /error. Since the response is already committed (200 OK SSE),
                // this causes "Cannot render error page" spam. Calling emitter.complete() here
                // finalizes the emitter cleanly BEFORE Spring's internal error handling kicks in.
                log.debug("SSE client disconnected or network error for repo: {} — completing emitter", repoId);
                emitters.remove(repoId);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // Emitter may already be in a broken state — safe to ignore
                }
            });

            log.info("Successfully established SSE connection for repo: {}", repoId);
            return emitter;
        } catch (Exception e) {
            log.error("Failed to establish SSE connection for repo: {}", repoId, e);
            throw new RuntimeException(e);
        }
    }

    @EventListener
    public void onIndexingFileEvent(IndexingFileEvent event) {
        SseEmitter emitter = emitters.get(event.getRepoId());
        if (emitter != null) {
            synchronized (emitter) {
                try {
                    log.debug("Sending SSE file_event to frontend for repoId={}, filePath={}, progress={}/{}", 
                        event.getRepoId(), event.getFilePath(), event.getProgress(), event.getTotalFiles());
                    emitter.send(SseEmitter.event()
                            .name("file_event")
                            .data(Map.of(
                                    "status", event.getStatus(), // "indexing" or "completed"
                                    "file_path", event.getFilePath(),
                                    "progress", event.getProgress(),
                                    "total_files", event.getTotalFiles()
                            )));
                } catch (IOException e) {
                    log.warn("Failed to send file_event for repo: {} — completing emitter to prevent error page redirect", event.getRepoId());
                    emitters.remove(event.getRepoId());
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // Emitter may already be in a broken state — safe to ignore
                    }
                }
            }
        }
    }

    @EventListener
    public void onIndexingStatusEvent(IndexingStatusEvent event) {
        SseEmitter emitter = emitters.get(event.getRepoId());
        if (emitter != null) {
            synchronized (emitter) {
                try {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", event.getStatus().name());
                    if (event.getErrorMessage() != null) {
                        data.put("errorMessage", event.getErrorMessage());
                    }
                    log.debug("Sending SSE status event to frontend for repoId={}, status={}", event.getRepoId(), event.getStatus());
                    emitter.send(SseEmitter.event()
                            .name("status")
                            .data(data));

                    if (event.getStatus() == RepositoryStatus.INDEXED || event.getStatus() == RepositoryStatus.FAILED) {
                        emitter.complete();
                        emitters.remove(event.getRepoId());
                    }
                } catch (IOException e) {
                    log.warn("Failed to send status event for repo: {} — completing emitter to prevent error page redirect", event.getRepoId());
                    emitters.remove(event.getRepoId());
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // Emitter may already be in a broken state — safe to ignore
                    }
                }
            }
        }
    }

    /**
     * Sends a keep-alive ping every 15 seconds to all open SSE connections.
     * This prevents intermediate reverse-proxies (like Nginx, AWS ALB, Cloudflare)
     * from forcefully closing the connection due to idle timeouts.
     */
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        emitters.forEach((repoId, emitter) -> {
            try {
                // Sending a named 'blink' event acts as a network heartbeat. AWS ALB/Nginx will not drop the connection.
                // The frontend should explicitly listen for 'blink' and safely ignore it.
                emitter.send(SseEmitter.event().name("blink").data("keep-alive"));
            } catch (IOException e) {
                // If the heartbeat fails, the connection is dead. Remove it to prevent memory leaks.
                log.debug("SSE Heartbeat failed for repo {}. Removing dead connection.", repoId);
                emitters.remove(repoId);
            }
        });
    }
}
