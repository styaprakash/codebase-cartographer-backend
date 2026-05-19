package com.codebasecartographer.api.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.dto.response.QueryResponse;
import com.codebasecartographer.api.entity.QueryLog;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.entity.User;
import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.exception.BadRequestException;
import com.codebasecartographer.api.exception.RateLimitException;
import com.codebasecartographer.api.exception.ResourceNotFoundException;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.QueryLogRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;
import com.codebasecartographer.api.repository.UserRepository;


@Service
public class QueryService {
    private final RepositoryRepository repositoryRepository;
    private final CodeChunkRepository codeChunkRepository;
    private final QueryLogRepository queryLogRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    //Constructor injection
    public QueryService(RepositoryRepository repositoryRepository, CodeChunkRepository codeChunkRepository, QueryLogRepository queryLogRepository, UserRepository userRepository, UserService userService){
        this.repositoryRepository = repositoryRepository; 
        this.codeChunkRepository = codeChunkRepository;  
        this.queryLogRepository = queryLogRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    // ── Main Query Method ─────────────────────────────────────────
    // Called by QueryController on POST /api/repos/:id/query
    @Transactional
    public QueryResponse query(String userId, String repoId, String question){
        // Guard checks (repo exists, user owns it, repo is indexed, query limit not reached) before processing the question
        Repository repo = verifyRepoAccess(userId, repoId);

        //Repo must be fully indexed before querying
        if(repo.getStatus() != RepositoryStatus.INDEXED){
            throw new BadRequestException(
                "Repository is not ready for querying as it is not indexed. Current status: " + repo.getStatus()
            );
        }
        // Check daily query limit (20/day)
        if(userService.isQueryLimitReached(userId)){
            throw new RateLimitException(userId, 20); // 20 queries per day limit
        }

          // Step 4 — TODO Week 5: Check Dragonfly cache
        // String cachedAnswer = dragonFlyService.getCachedAnswer(repoId, question)
        // if (cachedAnswer != null) return toResponse(cachedAnswer, ...)

        // Step 5 — TODO Week 5: Embed the question via Bedrock
        // float[] questionVector = embeddingService.embed(question)

        // Step 6 — TODO Week 5: Vector search via pgvector
        // List<CodeChunk> relevantChunks = codeChunkRepository
        //     .findTopSimilarChunks(repoId, questionVector, 10)

        // Step 7 — TODO Week 5: Agentic RAG loop via LangChain4j
        // String answer = agentService.runReActLoop(question, relevantChunks)

        // Step 8 — Placeholder answer until Week 5
        String answer = "AI response coming in week 5. " +
                        "Question received: " + question;

        int tokensUsed = 0;
        List<String> sourceFiles = Collections.emptyList();

        // Step 9 — Log the query + answer in QueryLog for history and analytics
        saveQueryLog(userId, repoId, question, answer, tokensUsed);

        // Step 10 — Increment daily count AFTER successful save
        userService.incrementQueryCount(userId);

        //step 11 - return the answer + metadata to frontend
        return QueryResponse.builder()
                .answer(answer)
                .sourceFiles(sourceFiles)
                .tokensUsed(tokensUsed)
                .build();
    }

    // ── Get Query History ─────────────────────────────────────────
    // Called by GET /api/repos/:id/queries
    // Returns all previous Q&A for this repo
    // Used to restore chat history when user reopens Repo Explorer
    public List<QueryResponse> getQueryHistory(String userId, String repoId) {
        // Verify access first
        verifyRepoAccess(userId, repoId);

        return queryLogRepository.findByRepository_Id(repoId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList()); 
    }

    //private method to check the verify user + belongs to same user
    private Repository verifyRepoAccess(String userId, String repoId){
        return repositoryRepository.findByUserIdAndId(userId, repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));
    }

    //private method to save query log
    // Saves question + answer to query_logs table
    // Called after every successful query
    private void saveQueryLog(String userId, String repoId, String question, String answer, int tokensUsed) {
        //check if the user exists
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        //check if the repository exists
        Repository repo = repositoryRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));

        QueryLog log = QueryLog.builder()
                .user(user)
                .repository(repo)
                .question(question)
                .answer(answer)
                .tokensUsed(tokensUsed)
                .build();

        queryLogRepository.save(log);
    }

    // ── Private: toResponse ───────────────────────────────────────
    // Converts QueryLog entity → QueryResponse DTO
    private QueryResponse toResponse(QueryLog log){
        return QueryResponse.builder()
                .answer(log.getAnswer())
                .tokensUsed(log.getTokensUsed())
                .sourceFiles(Collections.emptyList())
                // Week 5: parse sourceChunks JSON → List<String>
                .build();
    }
}
