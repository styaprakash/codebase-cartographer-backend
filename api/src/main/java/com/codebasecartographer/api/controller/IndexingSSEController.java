package com.codebasecartographer.api.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.codebasecartographer.api.enums.RepositoryStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/repos")
public class IndexingSSEController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @SuppressWarnings("null")
    @GetMapping(value = "/{repoId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String repoId) {
        SseEmitter emitter = new SseEmitter(3600000L); // 1 hour timeout
        emitters.put(repoId, emitter);

        emitter.onCompletion(() -> emitters.remove(repoId));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(repoId);
        });
        emitter.onError((e) -> {
            emitter.completeWithError(e);
            emitters.remove(repoId);
        });

        return emitter;
    }

    @SuppressWarnings("null")
    public void sendProgress(String repoId, int current, int total, int percentage, String currentFile) {
        SseEmitter emitter = emitters.get(repoId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(Map.of(
                                "current", current,
                                "total", total,
                                "percentage", percentage,
                                "currentFile", currentFile != null ? currentFile : ""
                        )));
            } catch (IOException e) {
                log.warn("Failed to send progress event, removing emitter for repo: {}", repoId);
                emitters.remove(repoId);
            }
        }
    }

    @SuppressWarnings("null")
    public void sendFileCompleted(String repoId, String fileName, int indexedCount) {
        SseEmitter emitter = emitters.get(repoId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("file_complete")
                        .data(Map.of(
                                "fileName", fileName,
                                "indexedCount", indexedCount
                        )));
            } catch (IOException e) {
                log.warn("Failed to send file_complete event, removing emitter for repo: {}", repoId);
                emitters.remove(repoId);
            }
        }
    }

    public void sendStatus(String repoId, RepositoryStatus status, String errorMessage) {
        SseEmitter emitter = emitters.get(repoId);
        if (emitter != null) {
            try {
                Map<String, Object> data = new java.util.HashMap<>();
                data.put("status", status);
                if (errorMessage != null) {
                    data.put("errorMessage", errorMessage);
                }
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(data));

                if (status == RepositoryStatus.INDEXED || status == RepositoryStatus.FAILED) {
                    emitter.complete();
                    emitters.remove(repoId);
                }
            } catch (IOException e) {
                log.warn("Failed to send status event, removing emitter for repo: {}", repoId);
                emitters.remove(repoId);
            }
        }
    }
}
