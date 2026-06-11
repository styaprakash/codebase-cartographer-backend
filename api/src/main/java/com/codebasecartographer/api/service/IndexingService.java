package com.codebasecartographer.api.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.dto.ASTChunk;
import com.codebasecartographer.api.dto.response.RepoResponse;
import com.codebasecartographer.api.entity.CodeChunk;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.enums.ProgrammingLanguage;
import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.exception.ConflictException;
import com.codebasecartographer.api.exception.ResourceNotFoundException;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;
import com.codebasecartographer.api.service.GitHubFileService.GithubFile;
import com.codebasecartographer.api.service.embeddingServices.EmbeddingModelSelector;
import com.codebasecartographer.api.service.embeddingServices.EmbeddingProviderMetrics;
import com.codebasecartographer.api.service.embeddingServices.factory.EmbeddingProviderFactory;
import com.codebasecartographer.api.service.embeddingServices.providers.EmbeddingProvider;
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
    private final EmbeddingProviderFactory providerFactory;
    private final EmbeddingModelSelector modelSelector;
    private final EmbeddingProviderMetrics providerMetrics;
    private final com.codebasecartographer.api.controller.IndexingSSEController sseController;
    private final InProcessASTChunkingService inProcessASTChunkingService;

    private static final int EMBEDDING_BATCH_SIZE = 20; // Adjust based on Ollama's limits

    public IndexingService(RepositoryRepository repositoryRepository,
            CodeChunkRepository codeChunkRepository,
            RepoService repoService,
            DragonflyQueueService dragonflyQueueService,
            GitHubFileService gitHubFileService,
            EmbeddingProviderFactory providerFactory,
            EmbeddingModelSelector modelSelector,
            EmbeddingProviderMetrics providerMetrics,
            com.codebasecartographer.api.controller.IndexingSSEController sseController,
            InProcessASTChunkingService inProcessASTChunkingService) {
        this.repositoryRepository = repositoryRepository;
        this.codeChunkRepository = codeChunkRepository;
        this.repoService = repoService;
        this.dragonflyQueueService = dragonflyQueueService;
        this.gitHubFileService = gitHubFileService;
        this.providerFactory = providerFactory;
        this.modelSelector = modelSelector;
        this.providerMetrics = providerMetrics;
        this.sseController = sseController;
        this.inProcessASTChunkingService = inProcessASTChunkingService;
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
    public void processIndexingJob(String repoId) {
        log.info("Starting indexing for repo: {}", repoId);

        Repository repository = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));

        if (repository.getEmbeddingModel() == null) {
            com.codebasecartographer.api.enums.EmbeddingModel selected = modelSelector.selectModel();
            repository.setEmbeddingModel(selected);
            repositoryRepository.save(repository);
            log.info("Assigned embedding model {} to repo {}", selected, repoId);
        }

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
            log.info("Found {} files to index", files.size());

            // Build and save chunks in batches with progress updates
            List<CodeChunk> chunks = new ArrayList<>();
            int processed = 0;
            for (GithubFile file : files) {
                try {
                    chunks.addAll(buildChunks(repository, file));
                } catch (Exception e) {
                    log.warn("Skipping file {}: {}", file.path(), e.getMessage());
                }
                processed++;

                // Flush to DB every 500 files WITH embeddings generated
                if (processed % 500 == 0) {
                    generateAndSaveEmbeddingsForBatch(repoId, chunks);

                    // Send SSE events
                    for (int i = 0; i < chunks.size(); i++) {
                        int currentCount = processed - chunks.size() + i + 1;
                        sseController.sendFileCompleted(repoId, chunks.get(i).getFilePath(), currentCount);
                    }
                    int percentage = (int) (((double) processed / files.size()) * 100);
                    sseController.sendProgress(repoId, processed, files.size(), percentage, file.path());

                    log.info("Saved batch of {} chunks with embeddings for repo: {}", chunks.size(),
                            repository.getFullName());
                    chunks.clear();
                }
            }

            // Flush remaining chunks
            if (!chunks.isEmpty()) {
                generateAndSaveEmbeddingsForBatch(repoId, chunks);

                for (int i = 0; i < chunks.size(); i++) {
                    int currentCount = processed - chunks.size() + i + 1;
                    sseController.sendFileCompleted(repoId, chunks.get(i).getFilePath(), currentCount);
                }
                sseController.sendProgress(repoId, processed, files.size(), 100, chunks.get(chunks.size() - 1).getFilePath());

                log.info("Saved final batch of {} chunks for repo: {}", chunks.size(), repository.getFullName());
            }

            // Mark as INDEXED
            repoService.updateStatus(repoId, RepositoryStatus.INDEXED);
            sseController.sendStatus(repoId, RepositoryStatus.INDEXED, null);
            log.info("Indexing complete for: {}", repository.getFullName());

        } catch (Exception e) {
            log.error("Indexing failed for repo: {}", repoId, e);
            String errorMessage = determineErrorMessage(e);
            repoService.setErrorMessage(repoId, errorMessage);
            sseController.sendStatus(repoId, RepositoryStatus.FAILED, errorMessage);
        }
    }

    private List<CodeChunk> buildChunks(Repository repo, GithubFile file) {
        ProgrammingLanguage language = ProgrammingLanguage.fromExtension(file.path());
        List<ASTChunk> astChunks = inProcessASTChunkingService.chunkCode(file.content(), language);

        List<CodeChunk> chunks = new ArrayList<>();
        for (ASTChunk astChunk : astChunks) {
            String entityName = astChunk.getEntityName();
            String chunkName = entityName != null ? entityName : FileUtils.extractFileName(file.path());

            chunks.add(CodeChunk.builder()
                    .repository(repo)
                    .filePath(file.path())
                    .chunkType(ASTChunk.mapChunkType(astChunk.getChunkType()))
                    .chunkName(chunkName)
                    .content(astChunk.getContent())
                    .startLine(astChunk.getStartLine())
                    .endLine(astChunk.getEndLine())
                    .scopeChain(astChunk.getScopeChain())
                    .entityName(entityName)
                    .aiReferenceCount(0)
                    .build());
        }
        return chunks;
    }

    // Fetch all chunks for this repo (no embedding yet), batch-call embeddings API,
    // save VECTOR(1536) to embedding column
    private void generateAndSaveEmbeddingsForBatch(String repoId, List<CodeChunk> chunks) {
        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));

        EmbeddingProvider provider = providerFactory.getProvider(repo.getEmbeddingModel());
        log.info("Using embedding model: {}", provider.getModel().getModelTag());
        log.info("Embedding dimension: {}", provider.getModel().getDimension());

        // Filter out empty chunks first
        List<CodeChunk> nonEmptyChunks = chunks.stream()
                .filter(chunk -> chunk.getContent() != null && !chunk.getContent().isBlank())
                .toList();

        log.info("Processing {} non-empty chunks", nonEmptyChunks.size());

        // Process in batches
        List<String> batchTexts = new ArrayList<>();
        List<CodeChunk> batchChunks = new ArrayList<>();

        for (int i = 0; i < nonEmptyChunks.size(); i++) {
            CodeChunk chunk = nonEmptyChunks.get(i);

            batchTexts.add(chunk.getContent());
            batchChunks.add(chunk);

            // When batch reaches size, embed the whole batch
            if (batchTexts.size() >= EMBEDDING_BATCH_SIZE) {
                embedBatchAndSave(repo, provider, batchTexts, batchChunks);
                batchTexts.clear();
                batchChunks.clear();
            }
        }

        // Process remaining chunks
        if (!batchTexts.isEmpty()) {
            embedBatchAndSave(repo, provider, batchTexts, batchChunks);
        }

        log.info("Completed embedding generation for repo {}", repoId);
    }

    // New helper method for batch embedding
    private void embedBatchAndSave(Repository repo, EmbeddingProvider provider,
            List<String> texts, List<CodeChunk> chunks) {
        try {
            providerMetrics.getOrCreate(repo.getEmbeddingModel()).markActive();
            long start = System.currentTimeMillis();

            // Batch embedding call - single API call for multiple texts
            List<float[]> embeddings = provider.embedBatch(texts);

            long latency = System.currentTimeMillis() - start;
            providerMetrics.recordSuccess(repo.getEmbeddingModel(), latency);

            // Assign embeddings to chunks
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setEmbedding(embeddings.get(i));
            }

            // Save batch to database
            codeChunkRepository.saveAll(chunks);
            log.info("Saved embedding batch of {} chunks (latency: {} ms)", chunks.size(), latency);

        } catch (Exception e) {
            providerMetrics.recordFailure(repo.getEmbeddingModel());
            log.error("Batch embedding failed for {} chunks", chunks.size(), e);

            // Optional: Fallback to individual embeddings for this batch
            log.info("Falling back to individual embeddings for {} chunks", chunks.size());
            fallbackToIndividualEmbeddings(repo, provider, chunks);
        }
    }

    // Optional: Fallback method if batch fails
    private void fallbackToIndividualEmbeddings(Repository repo, EmbeddingProvider provider,
            List<CodeChunk> chunks) {
        List<CodeChunk> savedChunks = new ArrayList<>();

        for (CodeChunk chunk : chunks) {
            try {
                long start = System.currentTimeMillis();
                float[] embedding = provider.embed(chunk.getContent());
                long latency = System.currentTimeMillis() - start;
                providerMetrics.recordSuccess(repo.getEmbeddingModel(), latency);

                chunk.setEmbedding(embedding);
                savedChunks.add(chunk);

                if (savedChunks.size() >= EMBEDDING_BATCH_SIZE) {
                    codeChunkRepository.saveAll(savedChunks);
                    savedChunks.clear();
                }
            } catch (Exception e) {
                providerMetrics.recordFailure(repo.getEmbeddingModel());
                log.error("Individual embedding failed for chunk {}", chunk.getId(), e);
            }
        }

        if (!savedChunks.isEmpty()) {
            codeChunkRepository.saveAll(savedChunks);
        }
    }

    @Transactional
    public void generateMissingEmbeddings(String repoId) {
        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));
        EmbeddingProvider provider = providerFactory.getProvider(repo.getEmbeddingModel());

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
                providerMetrics.getOrCreate(repo.getEmbeddingModel()).markActive();
                long start = System.currentTimeMillis();

                float[] embedding = provider.embed(chunk.getContent());

                long latency = System.currentTimeMillis() - start;
                providerMetrics.recordSuccess(repo.getEmbeddingModel(), latency);

                chunk.setEmbedding(embedding);
            } catch (Exception e) {
                providerMetrics.recordFailure(repo.getEmbeddingModel());
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
