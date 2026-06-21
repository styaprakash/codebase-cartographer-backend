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
import com.codebasecartographer.api.dto.IncrementalJobPayload;

import org.springframework.context.ApplicationEventPublisher;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import com.codebasecartographer.api.event.IndexingFileEvent;
import com.codebasecartographer.api.event.IndexingStatusEvent;
import java.nio.charset.StandardCharsets;

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
    private final ApplicationEventPublisher eventPublisher;
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
            ApplicationEventPublisher eventPublisher,
            InProcessASTChunkingService inProcessASTChunkingService) {
        this.repositoryRepository = repositoryRepository;
        this.codeChunkRepository = codeChunkRepository;
        this.repoService = repoService;
        this.dragonflyQueueService = dragonflyQueueService;
        this.gitHubFileService = gitHubFileService;
        this.providerFactory = providerFactory;
        this.modelSelector = modelSelector;
        this.providerMetrics = providerMetrics;
        this.eventPublisher = eventPublisher;
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
        String[] parts = repository.getFullName().split("/");
        String owner = parts[0];
        String repoName = parts[1];
        String branch = repository.getBranch();

        try {
            log.info("Fetching zipball from GitHub for: {}", repository.getFullName());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repoName + "/zipball/" + branch))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch zipball, status: " + response.statusCode());
            }

            int threads = 20;
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
            List<CodeChunk> sharedBuffer = java.util.Collections.synchronizedList(new ArrayList<>());
            List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

            java.util.concurrent.atomic.AtomicInteger processed = new java.util.concurrent.atomic.AtomicInteger(0);

            try (ZipInputStream zis = new ZipInputStream(response.body())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String path = entry.getName();
                    int firstSlash = path.indexOf('/');
                    if (firstSlash != -1) {
                        path = path.substring(firstSlash + 1);
                    }

                    if (!isSupportedFile(path)) {
                        continue;
                    }

                    byte[] contentBytes = zis.readAllBytes();
                    String content = new String(contentBytes, StandardCharsets.UTF_8);

                    final String filePath = path;
                    final GitHubFileService.GithubFile file = new GitHubFileService.GithubFile(filePath, content);

                    int currentIndex = processed.incrementAndGet();
                    
                    eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "indexing", filePath, currentIndex, 0));

                    futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            List<CodeChunk> fileChunks = buildChunks(repository, file);

                            if (!fileChunks.isEmpty()) {
                                List<List<CodeChunk>> batchesToProcess = new ArrayList<>();
                                synchronized (sharedBuffer) {
                                    sharedBuffer.addAll(fileChunks);
                                    while (sharedBuffer.size() >= EMBEDDING_BATCH_SIZE) {
                                        List<CodeChunk> batch = new ArrayList<>(sharedBuffer.subList(0, EMBEDDING_BATCH_SIZE));
                                        sharedBuffer.subList(0, EMBEDDING_BATCH_SIZE).clear();
                                        batchesToProcess.add(batch);
                                    }
                                }

                                for (List<CodeChunk> batch : batchesToProcess) {
                                    generateAndSaveEmbeddingsForBatch(repoId, batch);
                                }
                            }

                            eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "completed", filePath, currentIndex, 0));

                        } catch (Exception e) {
                            log.warn("Skipping file {}: {}", filePath, e.getMessage());
                            eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "completed", filePath, currentIndex, 0));
                        }
                    }, executor));
                }
            }

            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            executor.shutdown();

            if (!sharedBuffer.isEmpty()) {
                generateAndSaveEmbeddingsForBatch(repoId, new ArrayList<>(sharedBuffer));
                sharedBuffer.clear();
            }

            repoService.updateStatus(repoId, RepositoryStatus.INDEXED);
            eventPublisher.publishEvent(new IndexingStatusEvent(this, repoId, RepositoryStatus.INDEXED, null));
            log.info("Indexing complete for: {}", repository.getFullName());

        } catch (Exception e) {
            log.error("Indexing failed for repo: {}", repoId, e);
            String errorMessage = determineErrorMessage(e);
            repoService.setErrorMessage(repoId, errorMessage);
            eventPublisher.publishEvent(new IndexingStatusEvent(this, repoId, RepositoryStatus.FAILED, errorMessage));
        }
    }

    public void processIncrementalJob(IncrementalJobPayload payload) {
        String repoId = payload.repoId();
        log.info("Starting incremental indexing for repo: {}", repoId);

        Repository repository = repositoryRepository.findById(repoId).orElse(null);
        if (repository == null) {
            log.warn("Repo {} not found, aborting incremental job", repoId);
            return;
        }

        String accessToken = repository.getUser().getAccessToken();
        String[] parts = repository.getFullName().split("/");
        String owner = parts[0];
        String repoName = parts[1];
        String branch = repository.getBranch();

        if (payload.removed() != null && !payload.removed().isEmpty()) {
            codeChunkRepository.deleteChunksByRepoIdAndFilePathIn(repoId, payload.removed());
            log.info("Deleted chunks for {} removed files", payload.removed().size());
        }

        List<String> filesToFetch = new ArrayList<>();
        if (payload.modified() != null && !payload.modified().isEmpty()) {
            codeChunkRepository.deleteChunksByRepoIdAndFilePathIn(repoId, payload.modified());
            log.info("Deleted old chunks for {} modified files", payload.modified().size());
            filesToFetch.addAll(payload.modified());
        }

        if (payload.added() != null && !payload.added().isEmpty()) {
            filesToFetch.addAll(payload.added());
        }

        if (filesToFetch.isEmpty()) {
            log.info("No files to fetch and embed for incremental job");
            return;
        }

        filesToFetch = filesToFetch.stream().filter(this::isSupportedFile).toList();

        int totalFiles = filesToFetch.size();
        if (totalFiles == 0) return;

        java.util.concurrent.atomic.AtomicInteger processed = new java.util.concurrent.atomic.AtomicInteger(0);
        int threads = Math.min(20, totalFiles);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        List<CodeChunk> sharedBuffer = java.util.Collections.synchronizedList(new ArrayList<>());
        List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

        for (String filePath : filesToFetch) {
            int currentIndex = processed.incrementAndGet();
            eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "indexing", filePath, currentIndex, totalFiles));

            futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    String content = gitHubFileService.fetchFileContent(owner, repoName, filePath, branch, accessToken);
                    if (content == null || content.isBlank()) {
                        eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "completed", filePath, currentIndex, totalFiles));
                        return;
                    }

                    GitHubFileService.GithubFile file = new GitHubFileService.GithubFile(filePath, content);
                    List<CodeChunk> fileChunks = buildChunks(repository, file);

                    if (!fileChunks.isEmpty()) {
                        List<List<CodeChunk>> batchesToProcess = new ArrayList<>();
                        synchronized (sharedBuffer) {
                            sharedBuffer.addAll(fileChunks);
                            while (sharedBuffer.size() >= EMBEDDING_BATCH_SIZE) {
                                List<CodeChunk> batch = new ArrayList<>(sharedBuffer.subList(0, EMBEDDING_BATCH_SIZE));
                                sharedBuffer.subList(0, EMBEDDING_BATCH_SIZE).clear();
                                batchesToProcess.add(batch);
                            }
                        }

                        for (List<CodeChunk> batch : batchesToProcess) {
                            generateAndSaveEmbeddingsForBatch(repoId, batch);
                        }
                    }

                    eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "completed", filePath, currentIndex, totalFiles));
                } catch (Exception e) {
                    log.warn("Skipping file {}: {}", filePath, e.getMessage());
                    eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "completed", filePath, currentIndex, totalFiles));
                }
            }, executor));
        }

        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        executor.shutdown();

        if (!sharedBuffer.isEmpty()) {
            generateAndSaveEmbeddingsForBatch(repoId, new ArrayList<>(sharedBuffer));
            sharedBuffer.clear();
        }

        log.info("Incremental indexing complete for: {}", repository.getFullName());
    }

    private boolean isSupportedFile(String path) {
        java.util.Set<String> SUPPORTED_EXTENSIONS = java.util.Set.of(
            ".java", ".ts", ".tsx", ".js", ".jsx",
            ".py", ".go", ".rs", ".cpp", ".c"
        );
        return SUPPORTED_EXTENSIONS.stream().anyMatch(path::endsWith);
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
