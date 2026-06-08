package com.codebasecartographer.api.worker;

import org.springframework.stereotype.Component;

import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;
import com.codebasecartographer.api.service.DragonflyQueueService;
import com.codebasecartographer.api.service.IndexingService;
import com.codebasecartographer.api.service.RepoService;
import com.codebasecartographer.api.entity.Repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class IndexingWorker {
    private final DragonflyQueueService dragonflyQueueService;
    private final IndexingService indexingService;
    private final RepoService repoService;
    private final RepositoryRepository repositoryRepository;
    private final CodeChunkRepository codeChunkRepository;

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
        Thread workerThread = new Thread(() -> {
            log.info("Indexing Worker started...");
            // Keep checking queue forever
            while (true) {
                try {
                    String repoId = dragonflyQueueService.dequeue(); // rightPop() internally
                    // dequeue() blocks for 2s if empty — no sleep needed
                    if (repoId != null) {
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
                                log.error("Repo {} has chunks but no embedding model assigned. Skipping self-heal.", repoId);
                                repoService.setErrorMessage(repoId, "Cannot self-heal: no embedding model assigned");
                                continue;
                            }

                            indexingService.generateMissingEmbeddings(repoId);

                            long totalAfter = codeChunkRepository.countByRepository_Id(repoId);

                            long embeddedAfter = codeChunkRepository.countByRepository_IdAndEmbeddingIsNotNull(repoId);

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
