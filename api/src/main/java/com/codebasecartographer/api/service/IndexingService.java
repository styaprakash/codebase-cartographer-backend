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
import com.codebasecartographer.api.exception.ResourceNotFoundException;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;
import com.codebasecartographer.api.service.GitHubFileService.GithubFile;
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

    public IndexingService(RepositoryRepository repositoryRepository,
            CodeChunkRepository codeChunkRepository,
            RepoService repoService,
            DragonflyQueueService dragonflyQueueService,
            GitHubFileService gitHubFileService) {
        this.repositoryRepository = repositoryRepository;
        this.codeChunkRepository = codeChunkRepository;
        this.repoService = repoService;
        this.dragonflyQueueService = dragonflyQueueService;
        this.gitHubFileService = gitHubFileService;
    }

    // Called when user clicks "Index repo" on dashboard
    // 1. Optionally updates repo metadata (handles GitHub rename)
    // 2. Prepares repo in DB (deletes old chunks, resets counters, sets INDEXING)
    // 3. Transaction commits
    // 4. THEN enqueues the job — worker never sees uncommitted state
    public RepoResponse triggerIndexing(String repoId, String name, String fullName, String branch, String language) {
        if (name != null || fullName != null || branch != null) {
            repoService.updateRepoMetadata(repoId, name, fullName, branch, language);
        }

        repoService.prepareIndexing(repoId);

        // Enqueue happens AFTER prepareIndexing transaction commits
        // Worker will always see committed INDEXING status
        dragonflyQueueService.enqueue(repoId);
        log.info("Triggered indexing for repo {} — job enqueued", repoId);

        return repoService.getRepoById(
                repositoryRepository.findById(repoId)
                        .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId))
                        .getUser().getId(),
                repoId);
    }

    // Polled by frontend every 3 seconds on progress page
    public RepoResponse getIndexingStatus(String repoId) {
        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id: ", repoId));

        return repoService.getRepoById(repo.getUser().getId(), repoId);
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
                    "No supported files found."
                );
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
        if (content == null || content.isEmpty()) return 0;
        return (int) content.lines().count();
    }

    // TODO Week 3: Replace with Amazon Bedrock Titan/Cohere embeddings
    // Fetch all chunks for this repo (no embedding yet), batch-call embeddings API,
    // save VECTOR(1536) to embedding column
    private void generateAndSaveEmbeddings(String repoId) {
        log.info("Embeddings generation skipped — pending Bedrock integration");
    }

    private String determineErrorMessage(Exception ex) {
        return "Indexing failed: " + ex.getMessage();
    }
}
