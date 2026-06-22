package com.codebasecartographer.api.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.codebasecartographer.api.entity.CodeChunk;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;
import com.codebasecartographer.api.service.embeddingServices.factory.EmbeddingProviderFactory;
import com.codebasecartographer.api.service.embeddingServices.providers.EmbeddingProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSearchService {
    private final CodeChunkRepository codeChunkRepository;
    private final RepositoryRepository repositoryRepository;
    private final EmbeddingProviderFactory embeddingProviderFactory;

    public List<CodeChunk> search(String repoId, String query, int limit) {
        log.info("Searching repo {} for query: {}", repoId, query);

        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoId));

        if (repo.getEmbeddingModel() == null) {
            throw new IllegalStateException("Repository has no embedding model assigned");
        }

        EmbeddingProvider provider = embeddingProviderFactory.getProvider(repo.getEmbeddingModel());
        float[] queryEmbedding = provider.embed(query);
        String vectorString = Arrays.toString(queryEmbedding);

        return codeChunkRepository.findTopSimilarChunks(repoId, vectorString, limit);
    }
}
