package com.codebasecartographer.api.worker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;
import com.codebasecartographer.api.service.DragonflyQueueService;
import com.codebasecartographer.api.service.IndexingService;
import com.codebasecartographer.api.service.RepoService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@DependsOn({"redisConnectionFactory", "dragonflyQueueService"})
public class IndexingWorker {
    private final DragonflyQueueService dragonflyQueueService;
    private final IndexingService indexingService;
    private final RepoService repoService;
    private final RepositoryRepository repositoryRepository;
    private final CodeChunkRepository codeChunkRepository;

    private volatile boolean running = true;
    private Thread workerThread;
    private ExecutorService executor;

    // constructor injection
    public IndexingWorker(DragonflyQueueService dragonflyQueueService,
            IndexingService indexingService,
            RepoService repoService,
            RepositoryRepository repositoryRepository,
            CodeChunkRepository codeChunkRepository) {
        this.dragonflyQueueService = dragonflyQueueService;
        this.indexingService = indexingService;
        this.repoService = repoService;
        this.repositoryRepository = repositoryRepository;
        this.codeChunkRepository = codeChunkRepository;
    }

    // Run this method automatically AFTER Spring creates the bean. Worker
    // auto-starts when backend boots.
    @PostConstruct
    public void startWorker() {
        executor = Executors.newFixedThreadPool(10);

        workerThread = new Thread(() -> {
            log.info("Indexing Worker started...");
            while (running) {
                try {
                    String repoId = dragonflyQueueService.dequeue();
                    if (repoId != null) {
                        executor.submit(() -> {
                            log.info("Repo {} -> INDEXING", repoId);
                            repoService.updateStatus(repoId, RepositoryStatus.INDEXING);

                            Repository repo = repositoryRepository.findById(repoId).orElse(null);

                            long total = codeChunkRepository.countByRepository_Id(repoId);

                            long embedded = codeChunkRepository.countByRepository_IdAndEmbeddingIsNotNull(repoId);

                            // This is for self healing from failed embeddings
                            if (total > 0 && embedded < total) {
                                log.warn(
                                        "Repo {} has missing embeddings. total={}, embedded={}",
                                        repoId,
                                        total,
                                        embedded);
                                if (repo != null && repo.getEmbeddingModel() == null) {
                                    log.error("Repo {} has chunks but no embedding model assigned. Skipping self-heal.",
                                            repoId);
                                    repoService.setErrorMessage(repoId,
                                            "Cannot self-heal: no embedding model assigned");
                                    return;
                                }

                                indexingService.generateMissingEmbeddings(repoId);

                                long totalAfter = codeChunkRepository.countByRepository_Id(repoId);

                                long embeddedAfter = codeChunkRepository
                                        .countByRepository_IdAndEmbeddingIsNotNull(repoId);

                                if (totalAfter == embeddedAfter) {
                                    repoService.updateStatus(
                                            repoId,
                                            RepositoryStatus.INDEXED);
                                    log.info(
                                            "Repo {} successfully repaired. {} / {} embeddings present.",
                                            repoId,
                                            embeddedAfter,
                                            totalAfter);
                                } else {
                                    log.warn(
                                            "Repo {} repair incomplete. {} / {} embeddings present.",
                                            repoId,
                                            embeddedAfter,
                                            totalAfter);
                                }
                            } else {
                                indexingService.processIndexingJob(repoId);
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
            log.info("Indexing Worker stopped");
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @PreDestroy
    public void stopWorker() {
        log.info("Shutting down IndexingWorker...");
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
        log.info("IndexingWorker shut down complete");
    }
}
