package com.codebasecartographer.api.service;

import com.codebasecartographer.api.dto.response.RepoResponse;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.exception.ResourceNotFoundException;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;

import jakarta.transaction.Transactional;

public class IndexingService {
    private final RepositoryRepository repositoryRepository;
    private final CodeChunkRepository codeChunkRepository;
    private final RepoService repoService;

    public IndexingService(RepositoryRepository repositoryRepository, CodeChunkRepository codeChunkRepository, RepoService repoService){
        this.repositoryRepository = repositoryRepository; 
        this.codeChunkRepository = codeChunkRepository;  
        this.repoService = repoService;
    }

    // Called when user clicks "Index repo" on dashboard
    // 1. Deletes old chunks if re-indexing
    // 2. Sets status → INDEXING
    // 3. Pushes job to SQS (TODO Week 3)
    // Returns updated repo so frontend can navigate to progress page
    @Transactional
    public RepoResponse triggerIndexing(String repoId){
        //Find the repo or else throw 404
        Repository repo = repositoryRepository.findById(repoId)
            .orElseThrow(() -> 
                new ResourceNotFoundException("Repository", "id", repoId));

        // If re-indexing — delete all old chunks first
        // Fresh start, no duplicate chunks
        long existingChunks = codeChunkRepository.countByRepository_Id(repoId);
        if(existingChunks > 0){
            codeChunkRepository.deleteByRepository_Id(repoId);
        }

        // Reset progress counters
        repo.setIndexedFiles(0);
        repo.setTotalFiles(0);
        repo.setErrorMessage(null);
        repo.setStatus(RepositoryStatus.INDEXING);
        repositoryRepository.save(repo);

        // TODO Week 3 — push job to AWS SQS queue
        // mqService.pushIndexingJob(repoId); //AWS sqs or redis or dragonfly

        // Return updated repo — frontend uses this to navigate
        return repoService.getRepoById(repo.getUser().getId(), repoId);
    }

    // this will be called by frontend polling every 3 seconds on progress page
    // Returns current status + file counts
    public RepoResponse getIndexingStatus(String repoId){
        Repository repo = repositoryRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository", "id: ", repoId));
        
        return repoService.getRepoById(repo.getUser().getId(), repoId);
    }

    // ═══════════════════════════════════════════════════════════════
    // PART 2 — Scaffold Only (Week 3)
    // These need SQS + GitHub API + Tree-sitter + Bedrock
    // Structure defined now, logic added Week 3
    // ═══════════════════════════════════════════════════════════════

    // Called by SQS worker when it picks up a job from the queue
    // Orchestrates the full indexing pipeline:
    // fetchFiles → parseAST → embedChunks → saveToDB
    @Transactional
    public void processIndexingJob(String repoId) {
        // TODO Week 3:
        // 1. Get repo from DB
        // 2. Get user's GitHub access token
        // 3. Call fetchRepoFiles()
        // 4. Call parseAndChunkFiles()
        // 5. Call generateAndSaveEmbeddings()
        // 6. Update status → INDEXED
        // On any failure → call handleIndexingFailure()
    }

    // Calls GitHub Trees API to get all file paths + content
    // Stores raw files in AWS S3
    private void fetchRepoFiles(String repoId, String githubRepoId, String accessToken, String branch) {
        // TODO Week 3:
        // 1. Call GitHub API: GET /repos/:owner/:repo/git/trees?recursive=1
        // 2. Filter by supported extensions (.java .ts .js .py .go)
        // 3. Fetch file content for each path
        // 4. Store in S3: s3://bucket/repoId/filePath
        // 5. Update totalFiles count in DB
    }

    // Uses Tree-sitter to parse each file into chunks
    // Splits by function/class boundaries — not character count
    private void parseAndChunkFiles(String repoId) {
        // TODO Week 3:
        // 1. Read files from S3
        // 2. Detect language per file
        // 3. Run Tree-sitter parser
        // 4. Extract functions, classes, modules as chunks
        // 5. Save chunks to code_chunks table (without embedding yet)
        // 6. Update indexedFiles count in DB after each file
    }

    // Calls Amazon Bedrock Titan/Cohere to embed each chunk
    // Saves vector to pgvector column in code_chunks
    private void generateAndSaveEmbeddings(String repoId) {
        // TODO Week 3:
        // 1. Fetch all chunks for this repo (no embedding yet)
        // 2. Batch chunks in groups of 100
        // 3. Call Bedrock Titan/Cohere embeddings API
        // 4. Save VECTOR(1536) to embedding column
        // 5. Update indexedFiles count as embeddings complete
    }

    // Called when anything fails during indexing pipeline
    // Updates repo status to FAILED with a reason
    private void handleIndexingFailure(String repoId, Exception ex) {
        // TODO Week 3:
        // Determine what went wrong and set a user-friendly message
        String message = determineErrorMessage(ex);
        repoService.setErrorMessage(repoId, message);
        // Status is set to FAILED inside setErrorMessage
    }

    // Converts raw Java exceptions to user-friendly messages
    private String determineErrorMessage(Exception ex) {
        // TODO Week 3:
        // Map common exceptions to readable messages:
        // GitHub 403 → "Repository access denied. Check permissions."
        // GitHub 429 → "GitHub rate limit hit. Try again in an hour."
        // Timeout    → "Indexing timed out. Try a smaller repository."
        // Default    → "Indexing failed. Please try again."
        return "Indexing failed. Please try again.";
    }
}

