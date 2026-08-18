package com.codebasecartographer.api.worker;

import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

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
import java.time.Duration;

@Slf4j
@Component
@DependsOn({ "redisConnectionFactory", "dragonflyQueueService" })
public class IndexingWorker implements StreamListener<String, MapRecord<String, String, String>> {
    
    private final DragonflyQueueService dragonflyQueueService;
    private final IndexingService indexingService;
    private final RepoService repoService;
    private final RepositoryRepository repositoryRepository;
    private final CodeChunkRepository codeChunkRepository;
    private final RedisConnectionFactory redisConnectionFactory;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private Subscription subscription;

    public IndexingWorker(DragonflyQueueService dragonflyQueueService,
                          IndexingService indexingService,
                          RepoService repoService,
                          RepositoryRepository repositoryRepository,
                          CodeChunkRepository codeChunkRepository,
                          RedisConnectionFactory redisConnectionFactory) {
        this.dragonflyQueueService = dragonflyQueueService;
        this.indexingService = indexingService;
        this.repoService = repoService;
        this.repositoryRepository = repositoryRepository;
        this.codeChunkRepository = codeChunkRepository;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @PostConstruct
    public void startWorker() {
        log.info("Starting Stream Listener for IndexingWorker...");
        
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options = 
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(1))
                .build();
                
        this.listenerContainer = StreamMessageListenerContainer.create(redisConnectionFactory, options);
        
        this.subscription = listenerContainer.receive(
            Consumer.from(DragonflyQueueService.CONSUMER_GROUP, "indexing-worker-node-1"),
            StreamOffset.create(DragonflyQueueService.INDEXING_STREAM, ReadOffset.lastConsumed()),
            this
        );
        
        this.listenerContainer.start();
        log.info("IndexingWorker stream listener started successfully");
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String repoId = message.getValue().get("repoId");
        if (repoId == null) {
            log.warn("Received stream message without repoId: {}", message.getId());
            dragonflyQueueService.complete(message.getId());
            return;
        }
        
        try {
            log.info("Repo {} -> INDEXING (Stream Record ID: {})", repoId, message.getId());
            repoService.updateStatus(repoId, RepositoryStatus.INDEXING);

            Repository repo = repositoryRepository.findById(repoId).orElse(null);
            long total = codeChunkRepository.countByRepository_Id(repoId);
            long embedded = codeChunkRepository.countByRepository_IdAndEmbeddingIsNotNull(repoId);

            // This is for self healing from failed embeddings
            if (total > 0 && embedded < total) {
                log.warn("Repo {} has missing embeddings. total={}, embedded={}", repoId, total, embedded);
                if (repo != null && repo.getEmbeddingModel() == null) {
                    log.error("Repo {} has chunks but no embedding model assigned. Skipping self-heal.", repoId);
                    repoService.setErrorMessage(repoId, "Cannot self-heal: no embedding model assigned");
                    return;
                }

                indexingService.generateMissingEmbeddings(repoId);

                long totalAfter = codeChunkRepository.countByRepository_Id(repoId);
                long embeddedAfter = codeChunkRepository.countByRepository_IdAndEmbeddingIsNotNull(repoId);

                if (totalAfter == embeddedAfter) {
                    repoService.updateStatus(repoId, RepositoryStatus.INDEXED);
                    log.info("Repo {} successfully repaired. {} / {} embeddings present.", repoId, embeddedAfter, totalAfter);
                } else {
                    log.warn("Repo {} repair incomplete. {} / {} embeddings present.", repoId, embeddedAfter, totalAfter);
                }
            } else {
                indexingService.processIndexingJob(repoId);
            }
        } catch (Throwable t) {
            log.error("SEVERE: Worker thread crashed for repo {}", repoId, t);
            if (t.getCause() != null) {
                log.error("Root cause: [{}] {}", t.getCause().getClass().getSimpleName(), t.getCause().getMessage());
            }
            try {
                repoService.updateStatus(repoId, RepositoryStatus.FAILED);
                repoService.setErrorMessage(repoId, "Worker crash: " + t.getMessage());
            } catch (Exception statusEx) {
                log.error("Failed to update repo status after crash", statusEx);
            }
        } finally {
            dragonflyQueueService.complete(message.getId());
        }
    }

    @PreDestroy
    public void stopWorker() {
        log.info("Shutting down IndexingWorker...");
        if (this.listenerContainer != null) {
            this.listenerContainer.stop();
        }
        log.info("IndexingWorker shut down complete");
    }
}
