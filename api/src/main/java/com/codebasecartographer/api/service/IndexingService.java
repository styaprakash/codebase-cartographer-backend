package com.codebasecartographer.api.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.dto.response.RepoResponse;
import com.codebasecartographer.api.entity.CodeChunk;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.enums.ChunkType;
import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.exception.ConflictException;
import com.codebasecartographer.api.exception.ResourceNotFoundException;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;
import com.codebasecartographer.api.service.GitHubFileService.GithubFile;
import com.codebasecartographer.api.service.embeddingServices.EmbeddingService;
import com.codebasecartographer.api.utils.FileUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IndexingService {
    private final RepositoryRepository repositoryRepository;
    private final CodeChunkRepository codeChunkRepository;
    private final RepoService repoService;
    private final DragonflyQueueService dragonflyQueueService;
    private final GitHubFileService gitHubFileService;
    private final EmbeddingService embeddingService;

    public IndexingService(RepositoryRepository repositoryRepository,
            CodeChunkRepository codeChunkRepository,
            RepoService repoService,
            DragonflyQueueService dragonflyQueueService,
            GitHubFileService gitHubFileService,
            EmbeddingService embeddingService) {
        this.repositoryRepository = repositoryRepository;
        this.codeChunkRepository = codeChunkRepository;
        this.repoService = repoService;
        this.dragonflyQueueService = dragonflyQueueService;
        this.gitHubFileService = gitHubFileService;
        this.embeddingService = embeddingService;
    }

    // Called when user clicks "Index repo" on dashboard
    // 1. Rejects if already PENDING or INDEXING
    // 2. Optionally updates repo metadata (handles GitHub rename)
    // 3. Prepares repo in DB (deletes old chunks, resets counters, sets PENDING)
    // 4. Transaction commits
    // 5. THEN enqueues the job — worker never sees uncommitted state
    public RepoResponse triggerIndexing(String repoId, String name, String fullName, String branch, String language) {
        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));
        RepositoryStatus current = repo.getStatus();
        if (current == RepositoryStatus.PENDING || current == RepositoryStatus.INDEXING) {
            throw new ConflictException("Repository is already queued or being indexed.");
        }

        if (name != null || fullName != null || branch != null) {
            repoService.updateRepoMetadata(repoId, name, fullName, branch, language);
        }

        repoService.prepareIndexing(repoId);

        // Enqueue happens AFTER prepareIndexing transaction commits
        // Worker will always see committed PENDING status
        dragonflyQueueService.enqueue(repoId);
        log.info("Triggered indexing for repo {} — job enqueued", repoId);

        return repoService.getRepoById(
                repositoryRepository.findById(repoId)
                        .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId))
                        .getUser().getId(),
                repoId);
    }

    // Polled by frontend every 3 seconds on progress page
    public RepoResponse getIndexingStatus(String userId, String repoId) {
        return repoService.getRepoById(userId, repoId);
    }

    // Called by IndexingWorker when it picks up a job from the Dragonfly queue
    // Orchestrates the full indexing pipeline:
    // fetchFiles → parseChunks → saveChunks → generateEmbeddings → mark INDEXED
    @Transactional
    public void processIndexingJob(String repoId) {
        log.info("Starting indexing for repo: {}", repoId);

        Repository repository = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));

        String accessToken = repository.getUser().getAccessToken();

        try {
            log.info("Fetching files from GitHub for: {}", repository.getFullName());
            List<GithubFile> files = gitHubFileService.fetchRepoFiles(repository, accessToken);

            if (files.isEmpty()) {
                repoService.setErrorMessage(
                        repoId,
                        "No supported files found.");
                log.warn("No supported files found for repo: {}", repository.getFullName());
                return;
            }
            repoService.updateProgress(repoId, 0, files.size());
            log.info("Found {} files to index", files.size());

            // Build and save chunks in batches with progress updates
            List<CodeChunk> chunks = new ArrayList<>();
            int processed = 0;
            for (GithubFile file : files) {
                try {
                    chunks.add(buildChunk(repository, file));
                } catch (Exception e) {
                    log.warn("Skipping file {}: {}", file.path(), e.getMessage());
                }
                processed++;

                // Update progress every 5 files so the frontend sees smooth updates
                if (processed % 5 == 0) {
                    repoService.updateProgress(repoId, processed, files.size());
                }

                // Flush to DB every 50 files to keep memory bounded
                if (processed % 50 == 0) {
                    codeChunkRepository.saveAll(chunks);
                    log.info("Saved batch of {} chunks for repo: {}", chunks.size(), repository.getFullName());
                    chunks.clear();
                }
            }

            // Flush remaining chunks
            if (!chunks.isEmpty()) {
                codeChunkRepository.saveAll(chunks);
                log.info("Saved final batch of {} chunks for repo: {}", chunks.size(), repository.getFullName());
            }

            // Generate and save embeddings
            generateAndSaveEmbeddings(repoId);

            // Mark as INDEXED
            repoService.updateProgress(repoId, files.size(), files.size());
            repoService.updateStatus(repoId, RepositoryStatus.INDEXED);
            log.info("Indexing complete for: {}", repository.getFullName());

        } catch (Exception e) {
            log.error("Indexing failed for repo: {}", repoId, e);
            repoService.setErrorMessage(repoId, determineErrorMessage(e));
        }
    }

    private CodeChunk buildChunk(Repository repo, GithubFile file) {
        return CodeChunk.builder()
                .repository(repo)
                .filePath(file.path())
                .chunkType(ChunkType.MODULE)
                .chunkName(FileUtils.extractFileName(file.path()))
                .content(file.content())
                .startLine(1)
                .endLine(countLines(file.content()))
                .aiReferenceCount(0)
                .build();
    }

    private int countLines(String content) {
        if (content == null || content.isEmpty())
            return 0;
        return (int) content.lines().count();
    }

    // Number of embeddings persisted per DB batch
    private static final int EMBEDDING_BATCH_SIZE = 25;

    // TODO Week 3: Replace with Amazon Bedrock Titan/Cohere embeddings
    // Fetch all chunks for this repo (no embedding yet), batch-call embeddings API,
    // save VECTOR(1536) to embedding column
    private void generateAndSaveEmbeddings(String repoId) {
        List<CodeChunk> chunks = codeChunkRepository.findByRepository_Id(repoId);
        log.info("Generating embeddings for {} chunks", chunks.size());

        // Buffer embeddings and save in batches.
        // Avoids one huge DB transaction for all chunks.
        List<CodeChunk> batch = new ArrayList<>();

        for (CodeChunk chunk : chunks) {
            // Skip empty files
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }

            try {
                // Generate vector via Ollama
                float[] embedding = embeddingService.embed(chunk.getContent());
                chunk.setEmbedding(embedding);

                batch.add(chunk);

                // Persist every N chunks
                if (batch.size() >= EMBEDDING_BATCH_SIZE) {
                    codeChunkRepository.saveAll(batch);
                    log.info("Saved embedding batch of {}", batch.size());
                    batch.clear();
                }
            } catch (Exception e) {
                log.error(
                        "Embedding failed for chunk {}",
                        chunk.getId(),
                        e);
            }
        }

        // Save remaining chunks
        if (!batch.isEmpty()) {
            codeChunkRepository.saveAll(batch);

            log.info("Saved final batch of {}", batch.size());
        }
        codeChunkRepository.saveAll(chunks);
    }

    @Transactional
    public void generateMissingEmbeddings(String repoId) {
        List<CodeChunk> chunks = codeChunkRepository.findByRepository_IdAndEmbeddingIsNull(repoId);

        log.info("Generating embeddings for {} chunks", chunks.size());

        List<CodeChunk> batch = new ArrayList<>();
        if (chunks.isEmpty()) {
            log.info("No missing embeddings for repo {}", repoId);
            return;
        }

        log.info("Generating {} missing for repo {}", chunks.size(), repoId);

        for (CodeChunk chunk : chunks) {
            try {
                float[] embedding = embeddingService.embed(chunk.getContent());

                chunk.setEmbedding(embedding);
            } catch (Exception e) {
                log.error("Failed embedding chunk {}", chunk.getId(), e);
            }
        }

        codeChunkRepository.saveAll(chunks);
        log.info("Missing embeddings completed for repo {}", repoId);
    }

    private String determineErrorMessage(Exception ex) {
        return "Indexing failed: " + ex.getMessage();
    }
}
