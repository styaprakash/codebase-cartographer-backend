package com.codebasecartographer.api.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Service;

import com.codebasecartographer.api.enums.EmbeddingModel;
import com.codebasecartographer.api.exception.GithubApiException;
import com.codebasecartographer.api.dto.ASTChunk;
import com.codebasecartographer.api.dto.response.RepoResponse;
import com.codebasecartographer.api.entity.CodeChunk;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.enums.ProgrammingLanguage;
import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.exception.BadRequestException;
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
    // Pure Java text splitter — replaces the native Tree-Sitter JNI library
    // that was causing UnsatisfiedLinkError crashes on non-glibc systems
    private final RecursiveTextSplitter recursiveTextSplitter;
    private final ChunkSaveService chunkSaveService;

    // Shared executor for parallel file processing
    private final ExecutorService fileProcessingExecutor = Executors.newFixedThreadPool(20);

    private static final int EMBEDDING_BATCH_SIZE = 5; // Safe batch size for local Ollama hardware

    public IndexingService(RepositoryRepository repositoryRepository,
            CodeChunkRepository codeChunkRepository,
            RepoService repoService,
            DragonflyQueueService dragonflyQueueService,
            GitHubFileService gitHubFileService,
            EmbeddingProviderFactory providerFactory,
            EmbeddingModelSelector modelSelector,
            EmbeddingProviderMetrics providerMetrics,
            ApplicationEventPublisher eventPublisher,
            RecursiveTextSplitter recursiveTextSplitter,
            ChunkSaveService chunkSaveService) {
        this.repositoryRepository = repositoryRepository;
        this.codeChunkRepository = codeChunkRepository;
        this.repoService = repoService;
        this.dragonflyQueueService = dragonflyQueueService;
        this.gitHubFileService = gitHubFileService;
        this.providerFactory = providerFactory;
        this.modelSelector = modelSelector;
        this.providerMetrics = providerMetrics;
        this.eventPublisher = eventPublisher;
        this.recursiveTextSplitter = recursiveTextSplitter;
        this.chunkSaveService = chunkSaveService;
    }

    // Called when user clicks "Index repo" on dashboard
    // 1. Rejects if already PENDING or INDEXING
    // 2. Optionally updates repo metadata (handles GitHub rename)
    // 3. Prepares repo in DB (deletes old chunks, resets counters, sets PENDING)
    // 4. Transaction commits
    // 5. THEN enqueues the job — worker never sees uncommitted state
    public RepoResponse triggerIndexing(String repoId, String name, String fullName, String branch, String language, String embeddingModel) {
        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));
        RepositoryStatus current = repo.getStatus();
        log.info("Trigger POST request received for repoId={}. Status is currently: {}. Proceeding...", repoId, current);
        if (current == RepositoryStatus.PENDING || current == RepositoryStatus.INDEXING) {
            log.warn("Status is stuck on {}. Rejecting...", current);
            throw new ConflictException("Repository is already queued or being indexed.");
        }

        if (name != null || fullName != null || branch != null) {
            repoService.updateRepoMetadata(repoId, name, fullName, branch, language);
        }

        if (embeddingModel != null && !embeddingModel.isBlank()) {
            // Validate the string matches a real enum constant (rejects garbage input)
            EmbeddingModel modelEnum;
            try {
                modelEnum = EmbeddingModel.valueOf(embeddingModel);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid embedding model: " + embeddingModel);
            }
            // Ensure the model is enabled before allowing it into the pipeline
            if (!modelEnum.isEnabled()) {
                throw new BadRequestException("Embedding model is not active: " + embeddingModel);
            }
            repo.setEmbeddingModel(modelEnum);
            repositoryRepository.save(repo);
            log.info("Assigned embedding model {} to repo {} from user selection", embeddingModel, repoId);
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

        EmbeddingModel selected = modelSelector.selectModel();
        repository.setEmbeddingModel(selected);
        repositoryRepository.save(repository);
        log.info("Assigned embedding model {} to repo {}", selected, repoId);

        // ALWAYS clear old chunks in the Vector DB to prevent duplicates and orphaned data
        codeChunkRepository.deleteChunksByRepoId(repoId);
        log.info("Cleared any existing chunks for repo {} before starting fresh indexing", repoId);

        String accessToken = repository.getUser().getAccessToken();
        String[] parts = repository.getFullName().split("/");
        String owner = parts[0].trim();
        String repoName = parts[1].trim();
        String branch = repository.getBranch();

        try {
            log.info("Fetching zipball from GitHub for: {}", repository.getFullName());
            String zipUrl = "https://api.github.com/repos/" + owner + "/" + repoName + "/zipball";
            if (branch != null && !branch.trim().isEmpty()) {
                zipUrl += "/" + branch.trim();
                log.info("Starting HTTP call to GitHub Archive API for zipball: {}/{} (branch: {})", owner, repoName, branch.trim());
            } else {
                log.info("Starting HTTP call to GitHub Archive API for zipball: {}/{} (default branch)", owner, repoName);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(zipUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Codebase-Cartographer")
                    .GET()
                    .build();

            // 1. Initial Request
            // We MUST include the Authorization header here because we are hitting api.github.com.
            // We set followRedirects(NEVER) because GitHub will return a 302 redirect to a different
            // domain (like codeload.github.com). If the Java HttpClient automatically follows this
            // redirect, it will send the Authorization header to the new domain, which causes a 400 Bad Request.
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER) // Manually handle redirects to avoid leaking tokens
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            log.info("HTTP Status Code returned from GitHub (Step 1): {}", response.statusCode());

            // 2. Handle Redirect
            if (response.statusCode() == 301 || response.statusCode() == 302 || response.statusCode() == 307) {
                // Extract the new URL from the Location header
                final int redirectCode = response.statusCode(); // Save to final var for lambda capture
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new GithubApiException("Redirect missing Location header", redirectCode));

                // GitHub's codeload redirect URLs sometimes end with a trailing slash that causes 404
                if (location.endsWith("/")) {
                    location = location.substring(0, location.length() - 1);
                }

                log.info("Handling GitHub redirect to: {}", location);
                // We must close the old input stream to prevent connection leaks
                response.body().close(); 

                // 3. Second Request (The Hop)
                // We build a NEW request to the redirected URL.
                // CRUCIAL: We do NOT include the Authorization header here.
                // The redirected URL contains temporary embedded credentials in its query string.
                HttpRequest redirectRequest = HttpRequest.newBuilder()
                        .uri(URI.create(location))
                        // STRICTLY NO Authorization header here!
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "Codebase-Cartographer")
                        .GET()
                        .build();

                response = client.send(redirectRequest, HttpResponse.BodyHandlers.ofInputStream());
                log.info("HTTP Status Code returned from redirect (Step 2): {}", response.statusCode());
            }

            if (response.statusCode() != 200) {
                log.error("Failed at Zip Download: GitHub API returned status {}", response.statusCode());
                if (response.statusCode() == 400) {
                    throw new GithubApiException("Repository is empty or un-downloadable (GitHub 400)", 400);
                }
                if (response.statusCode() == 404) {
                    throw new GithubApiException(
                            "Repository branch not found (GitHub 404). Ensure the branch '"
                                    + (branch != null ? branch : "default")
                                    + "' exists and the repository is not empty.", 404);
                }
                throw new GithubApiException("Failed to fetch zipball, GitHub returned status: " + response.statusCode(), response.statusCode());
            }

            // 1. First Pass: Collect all supported files to determine totalFiles
            List<GitHubFileService.GithubFile> filesToProcess = new ArrayList<>();
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
                    filesToProcess.add(new GitHubFileService.GithubFile(path, content));
                }
            }

            final int totalFiles = filesToProcess.size();
            repoService.updateProgress(repoId, 0, totalFiles);

            // 2. Empty Repo Check
            if (totalFiles == 0) {
                repoService.setErrorMessage(repoId, "No supported files found.");
                eventPublisher.publishEvent(new IndexingStatusEvent(this, repoId, 
                    RepositoryStatus.FAILED, "No supported files found."));
                return;
            }

            // 3. Second Pass: Process files in parallel
            List<CodeChunk> sharedBuffer = Collections.synchronizedList(new ArrayList<>());
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            AtomicInteger started = new AtomicInteger(0);
            AtomicInteger processed = new AtomicInteger(0);

            for (GitHubFileService.GithubFile file : filesToProcess) {
                final String filePath = file.path();

                // Submit file processing to the thread pool for parallel execution
                futures.add(CompletableFuture.runAsync(() -> {
                    int currentIndex = started.incrementAndGet();
                    eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "indexing", filePath, currentIndex, totalFiles));

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
                                generateAndSaveEmbeddingsForBatch(repository, batch);
                            }
                        }
                    } catch (Throwable t) {
                        // CATCH THROWABLE, NOT EXCEPTION!
                        // UnsatisfiedLinkError, OutOfMemoryError, StackOverflowError are all
                        // java.lang.Error subclasses that bypass catch(Exception). Catching
                        // Throwable ensures the thread doesn't die silently, which would
                        // cause the SSE stream to hang indefinitely.
                        log.error("SEVERE: Failed processing file {} (possible JVM Error)", filePath, t);
                    } finally {
                        int completedCount = processed.incrementAndGet();
                        repoService.updateProgress(repoId, completedCount, totalFiles);
                        eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "completed", filePath, completedCount, totalFiles));
                    }
                }, fileProcessingExecutor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            if (!sharedBuffer.isEmpty()) {
                List<CodeChunk> remainingChunks = new ArrayList<>(sharedBuffer);
                sharedBuffer.clear();
                try {
                    generateAndSaveEmbeddingsForBatch(repository, remainingChunks);
                } catch (Exception e) {
                    log.error("Failed to save remaining {} chunks after parallel processing", remainingChunks.size(), e);
                }
            }

            // 4. Post-join verification: confirm chunks actually landed in DB
            long savedChunkCount = codeChunkRepository.countByRepository_Id(repoId);
            if (savedChunkCount == 0) {
                log.error("Indexing completed but zero chunks found in DB for repo {}. Marking as FAILED.", repoId);
                repoService.setErrorMessage(repoId, "Indexing completed but no code chunks were saved to the database.");
                eventPublisher.publishEvent(new IndexingStatusEvent(this, repoId, RepositoryStatus.FAILED,
                        "No code chunks saved to database"));
                return;
            }

            // 5. Final Progress Update
            repoService.updateProgress(repoId, totalFiles, totalFiles);
            repoService.updateStatus(repoId, RepositoryStatus.INDEXED);
            eventPublisher.publishEvent(new IndexingStatusEvent(this, repoId, RepositoryStatus.INDEXED, null));
            log.info("Indexing complete for: {} — {} chunks saved to DB", repository.getFullName(), savedChunkCount);

        } catch (Throwable t) {
            // Catch Throwable to handle JVM-level Errors (UnsatisfiedLinkError, OOM, etc.)
            // that would otherwise kill the thread silently and leave the repo stuck on INDEXING
            log.error("Indexing failed for repo: {}", repoId, t);
            String errorMessage = determineErrorMessage(t);
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

        AtomicInteger started = new AtomicInteger(0);
        AtomicInteger processed = new AtomicInteger(0);
        List<CodeChunk> sharedBuffer = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String filePath : filesToFetch) {
            futures.add(CompletableFuture.runAsync(() -> {
                int currentIndex = started.incrementAndGet();
                eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "indexing", filePath, currentIndex, totalFiles));

                try {
                    String content = gitHubFileService.fetchFileContent(owner, repoName, filePath, branch, accessToken);
                    if (content == null || content.isBlank()) {
                        int completedCount = processed.incrementAndGet();
                        eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "completed", filePath, completedCount, totalFiles));
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
                            generateAndSaveEmbeddingsForBatch(repository, batch);
                        }
                    }

                    int completedCount = processed.incrementAndGet();
                    eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "completed", filePath, completedCount, totalFiles));
                } catch (Exception e) {
                    log.warn("Skipping file {}: {}", filePath, e.getMessage());
                    int completedCount = processed.incrementAndGet();
                    eventPublisher.publishEvent(new IndexingFileEvent(this, repoId, "completed", filePath, completedCount, totalFiles));
                }
            }, fileProcessingExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (!sharedBuffer.isEmpty()) {
            List<CodeChunk> remainingChunks = new ArrayList<>(sharedBuffer);
            sharedBuffer.clear();
            try {
                generateAndSaveEmbeddingsForBatch(repository, remainingChunks);
            } catch (Exception e) {
                log.error("Failed to save remaining {} chunks after incremental processing", remainingChunks.size(), e);
            }
        }

        log.info("Incremental indexing complete for: {}", repository.getFullName());
    }

    private boolean isSupportedFile(String path) {
        Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".ts", ".tsx", ".js", ".jsx",
            ".py", ".go", ".rs", ".cpp", ".c"
        );
        return SUPPORTED_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private List<CodeChunk> buildChunks(Repository repo, GithubFile file) {
        ProgrammingLanguage language = ProgrammingLanguage.fromExtension(file.path());
        // Use the pure Java recursive text splitter instead of native Tree-Sitter
        // Language detection is kept for potential future use but not required for splitting
        List<ASTChunk> astChunks = recursiveTextSplitter.chunkCode(file.content());

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
    protected void generateAndSaveEmbeddingsForBatch(Repository repo, List<CodeChunk> chunks) {
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

        log.info("Completed embedding generation for repo {}", repo.getId());
    }



    // New helper method for batch embedding
    private void embedBatchAndSave(Repository repo, EmbeddingProvider provider,
            List<String> texts, List<CodeChunk> chunks) {
        try {
            providerMetrics.getOrCreate(repo.getEmbeddingModel()).markActive();
            long start = System.currentTimeMillis();

            // Batch embedding call - single API call for multiple texts
            log.info("Sending batch of {} chunks to LLM provider for embedding...", texts.size());
            List<float[]> embeddings = provider.embedBatch(texts);

            long latency = System.currentTimeMillis() - start;
            providerMetrics.recordSuccess(repo.getEmbeddingModel(), latency);

            // Assign embeddings to chunks
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setEmbedding(embeddings.get(i));
            }

            // Save batch to database via ChunkSaveService (proper @Transactional)
            chunkSaveService.saveChunks(chunks);
            log.info("Vectors successfully saved to Postgres DB. Batch of {} chunks (latency: {} ms)", chunks.size(), latency);

        } catch (Exception e) {
            providerMetrics.recordFailure(repo.getEmbeddingModel());

            // Unwrap the cause chain to find the root exception
            Throwable rootCause = e;
            while (rootCause.getCause() != null) rootCause = rootCause.getCause();

            // AUTH FAILURE: API key rejected — fallback will hit the same 401/403, abort immediately
            if (rootCause instanceof HttpClientErrorException.Unauthorized
                    || rootCause instanceof HttpClientErrorException.Forbidden) {
                String errorType = rootCause.getClass().getSimpleName();
                log.error("AUTH FAILURE: API key rejected by {} — aborting indexing. [{}] {}",
                        repo.getEmbeddingModel(), errorType, rootCause.getMessage());
                throw new RuntimeException("Embedding API authentication failed: " + rootCause.getMessage(), e);
            }

            // DB integrity failure (e.g., pgvector dimension mismatch) — also abort, fallback won't help
            if (rootCause instanceof DataIntegrityViolationException
                    || (e.getCause() != null && e.getCause() instanceof DataIntegrityViolationException)) {
                log.error("DATABASE INTEGRITY ERROR: Failed to save {} chunks. Likely dimension mismatch between embedding provider ({}) and DB column. [{}] {}",
                        chunks.size(), repo.getEmbeddingModel(), rootCause.getClass().getSimpleName(), rootCause.getMessage(), e);
                throw new RuntimeException("Database save failed: " + rootCause.getMessage(), e);
            }

            // Dimension mismatch — provider returns wrong size, fallback will hit same error
            if (rootCause instanceof IllegalStateException
                    || (rootCause.getMessage() != null && rootCause.getMessage().startsWith("DIMENSION MISMATCH"))) {
                log.error("DIMENSION MISMATCH: Embedding provider {} returned wrong dimensions. "
                        + "Aborting indexing — fallback would hit the same error.",
                        repo.getEmbeddingModel());
                throw new RuntimeException("Embedding dimension mismatch: " + rootCause.getMessage(), e);
            }

            // Transient errors (rate limits, timeouts, 5xx) — fallback may succeed
            String errorType = e.getClass().getSimpleName();
            log.error("EMBEDDING PIPELINE ERROR: Batch failed for {} chunks. [{}] {}",
                    chunks.size(), errorType, e.getMessage(), e);

            log.info("Falling back to individual embeddings for {} chunks", chunks.size());
            fallbackToIndividualEmbeddings(repo, provider, chunks);
        }
    }

    // Fallback: embed and save chunks individually when batch fails
    private void fallbackToIndividualEmbeddings(Repository repo, EmbeddingProvider provider,
            List<CodeChunk> chunks) {
        List<CodeChunk> savedChunks = new ArrayList<>();
        int failedCount = 0;

        for (CodeChunk chunk : chunks) {
            try {
                long start = System.currentTimeMillis();
                float[] embedding = provider.embed(chunk.getContent());
                long latency = System.currentTimeMillis() - start;
                providerMetrics.recordSuccess(repo.getEmbeddingModel(), latency);

                chunk.setEmbedding(embedding);
                savedChunks.add(chunk);

                if (savedChunks.size() >= EMBEDDING_BATCH_SIZE) {
                    chunkSaveService.saveChunks(savedChunks);
                    savedChunks.clear();
                }
            } catch (Exception e) {
                providerMetrics.recordFailure(repo.getEmbeddingModel());
                failedCount++;
                log.error("Chunk save failed for file={}, name={}: ",
                        chunk.getFilePath(), chunk.getChunkName(), e);
            }
        }

        if (!savedChunks.isEmpty()) {
            try {
                chunkSaveService.saveChunks(savedChunks);
            } catch (Exception e) {
                failedCount += savedChunks.size();
                log.error("Final fallback batch save failed for {} chunks: ",
                        savedChunks.size(), e);
            }
        }

        log.warn("Fallback summary for batch of {} chunks: saved={}, failed={}",
                chunks.size(), chunks.size() - failedCount, failedCount);
    }

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

        log.info("Generating {} missing chunks for repo {}", chunks.size(), repoId);

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
                log.error("Failed embedding chunk in file={}, name={}: [{}] {}",
                        chunk.getFilePath(), chunk.getChunkName(),
                        e.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        chunkSaveService.saveChunks(chunks);
        log.info("Missing embeddings completed for repo {}", repoId);
    }

    // Accepts Throwable (not just Exception) so we can handle JVM Errors
    // like UnsatisfiedLinkError, OutOfMemoryError, etc.
    private String determineErrorMessage(Throwable t) {
        String rootType = t.getClass().getSimpleName();
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return "Indexing failed [" + rootType + " → " + cause.getClass().getSimpleName() + "]: " + cause.getMessage();
        }
        return "Indexing failed [" + rootType + "]: " + t.getMessage();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOrphanedJobs() {
        log.info("Running startup sweep to clean up orphaned indexing jobs...");
        List<Repository> orphaned = repositoryRepository.findByStatus(RepositoryStatus.INDEXING);
        for (Repository repo : orphaned) {
            log.warn("Found orphaned job for repo {}. Marking as FAILED.", repo.getId());
            repoService.updateStatus(repo.getId(), RepositoryStatus.FAILED);
            repoService.setErrorMessage(repo.getId(), "Indexing interrupted: Server was shut down unexpectedly.");
            codeChunkRepository.deleteChunksByRepoId(repo.getId());
            log.info("Deleted orphaned chunks for repo {} in Vector DB", repo.getId());
        }
        log.info("Startup sweep complete. Cleaned up {} orphaned jobs.", orphaned.size());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down IndexingService file processing executor...");
        fileProcessingExecutor.shutdown();
        try {
            if (!fileProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in the specified time. Forcing shutdown...");
                fileProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Executor shutdown interrupted. Forcing shutdown...");
            fileProcessingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
