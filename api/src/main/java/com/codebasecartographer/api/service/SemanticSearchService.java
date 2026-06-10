package com.codebasecartographer.api.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.codebasecartographer.api.dto.response.SearchResult;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.exception.ResourceNotFoundException;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;
import com.codebasecartographer.api.service.embeddingServices.factory.EmbeddingProviderFactory;
import com.codebasecartographer.api.service.embeddingServices.providers.EmbeddingProvider;

@Service
public class SemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

    private final CodeChunkRepository codeChunkRepository;
    private final RepositoryRepository repositoryRepository;
    private final EmbeddingProviderFactory providerFactory;
    private final QueryCacheService queryCacheService;

    public SemanticSearchService(
            CodeChunkRepository codeChunkRepository,
            RepositoryRepository repositoryRepository,
            EmbeddingProviderFactory providerFactory,
            QueryCacheService queryCacheService) {
        this.codeChunkRepository = codeChunkRepository;
        this.repositoryRepository = repositoryRepository;
        this.providerFactory = providerFactory;
        this.queryCacheService = queryCacheService;
    }

    public List<SearchResult> search(String repoId, String query, Double threshold, Integer limit) {
        double finalThreshold = threshold != null ? threshold : 0.7;
        int finalLimit = limit != null && limit > 0 ? Math.min(limit, 50) : 10;

        log.info("Semantic search - repoId: {}, query: {}, threshold: {}, limit: {}",
                repoId, query, finalThreshold, finalLimit);

        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));

        EmbeddingProvider provider = providerFactory.getProvider(repo.getEmbeddingModel());

        float[] queryEmbedding = queryCacheService.getCachedEmbedding(query);
        if (queryEmbedding == null) {
            long start = System.currentTimeMillis();
            queryEmbedding = provider.embed(query);
            long embeddingTime = System.currentTimeMillis() - start;
            log.info("Query embedding completed in {} ms, dimension: {}", embeddingTime, queryEmbedding.length);
            queryCacheService.cacheEmbedding(query, queryEmbedding);
        } else {
            log.info("Using cached embedding for query: {}", query);
        }

        String vectorString = Arrays.toString(queryEmbedding);

        List<Object[]> results = codeChunkRepository.findSimilarChunks(
                repoId, vectorString, finalThreshold, finalLimit);

        log.info("Found {} results with similarity >= {}", results.size(), finalThreshold);

        List<SearchResult> searchResults = new ArrayList<>();
        for (Object[] row : results) {
            SearchResult result = SearchResult.builder()
                    .chunkId((String) row[0])
                    .content((String) row[1])
                    .filePath((String) row[2])
                    .startLine(row[3] != null ? (Integer) row[3] : null)
                    .endLine(row[4] != null ? (Integer) row[4] : null)
                    .similarity((Double) row[5])
                    .build();
            searchResults.add(result);
        }

        return searchResults;
    }
}
