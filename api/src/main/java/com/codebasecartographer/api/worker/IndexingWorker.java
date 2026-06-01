package com.codebasecartographer.api.worker;

import org.springframework.stereotype.Component;

import com.codebasecartographer.api.service.DragonflyQueueService;
import com.codebasecartographer.api.service.IndexingService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class IndexingWorker {
    private final DragonflyQueueService dragonflyQueueService;
    private final IndexingService indexingService;

    // constructor injection
    public IndexingWorker(DragonflyQueueService dragonflyQueueService, 
                        IndexingService indexingService) {
        this.dragonflyQueueService = dragonflyQueueService;
        this.indexingService = indexingService;
    }

    // Run this method automatically AFTER Spring creates the bean. Worker
    // auto-starts when backend boots.
    @PostConstruct
    public void startWorker() {
        Thread workerThread = new Thread(() -> {
            log.info("Indexing Worker started...");
            // Keep checking queue forever
            while (true) {
                try {
                    String repoId = dragonflyQueueService.dequeue(); // rightPop() internally
                    // dequeue() blocks for 2s if empty — no sleep needed
                    if (repoId != null) {
                        log.info("Processing indexing job for repo: {}", repoId);
                        indexingService.processIndexingJob(repoId);
                    }
                } catch (Exception ex) {
                    log.error("Worker error", ex);
                }
            }
        });
        workerThread.setDaemon(true); // Thread should die automatically when app shuts down.
        workerThread.start();
    }
}
