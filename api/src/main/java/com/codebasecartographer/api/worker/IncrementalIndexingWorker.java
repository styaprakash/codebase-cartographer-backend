package com.codebasecartographer.api.worker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import com.codebasecartographer.api.dto.IncrementalJobPayload;
import com.codebasecartographer.api.service.DragonflyQueueService;
import com.codebasecartographer.api.service.IndexingService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@DependsOn({"redisConnectionFactory", "dragonflyQueueService"})
public class IncrementalIndexingWorker {

    private final DragonflyQueueService dragonflyQueueService;
    private final IndexingService indexingService;
    private final ObjectMapper objectMapper;

    private volatile boolean running = true;
    private Thread workerThread;
    private ExecutorService executor;

    public IncrementalIndexingWorker(DragonflyQueueService dragonflyQueueService,
                                     IndexingService indexingService,
                                     ObjectMapper objectMapper) {
        this.dragonflyQueueService = dragonflyQueueService;
        this.indexingService = indexingService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startWorker() {
        executor = Executors.newFixedThreadPool(10);

        workerThread = new Thread(() -> {
            log.info("Incremental Indexing Worker started...");
            while (running) {
                try {
                    String payloadJson = dragonflyQueueService.dequeueIncremental();
                    if (payloadJson != null) {
                        executor.submit(() -> {
                            try {
                                IncrementalJobPayload payload = objectMapper.readValue(payloadJson, IncrementalJobPayload.class);
                                indexingService.processIncrementalJob(payload);
                            } catch (Exception e) {
                                log.error("Failed to process incremental job", e);
                            } finally {
                                dragonflyQueueService.completeIncremental(payloadJson);
                            }
                        });
                    }
                } catch (IllegalStateException ex) {
                    if (ex.getMessage() != null && (ex.getMessage().contains("destroyed") || ex.getMessage().contains("STOPPED"))) {
                        log.info("Redis connection destroyed or stopped, stopping worker");
                        break;
                    }
                    log.error("Worker error", ex);
                } catch (Exception ex) {
                    log.error("Worker error", ex);
                }
            }
            log.info("Incremental Indexing Worker stopped");
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @PreDestroy
    public void stopWorker() {
        log.info("Shutting down IncrementalIndexingWorker...");
        running = false;
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("IncrementalIndexingWorker shut down complete");
    }
}
