package com.codebasecartographer.api.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.codebasecartographer.api.entity.CodeChunk;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.enums.ChunkType;
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

        // Returns List<Object[]> to avoid pgvector JDBC crash from loading embedding column
        List<Object[]> results = codeChunkRepository.findTopSimilarChunks(repoId, vectorString, limit);

        return results.stream()
                .map(row -> CodeChunk.builder()
                        .id((String) row[0])
                        .content((String) row[1])
                        .filePath((String) row[2])
                        .startLine(row[3] != null ? ((Number) row[3]).intValue() : null)
                        .endLine(row[4] != null ? ((Number) row[4]).intValue() : null)
                        .chunkType(ChunkType.valueOf((String) row[5]))
                        .chunkName((String) row[6])
                        .build())
                .collect(Collectors.toList());
    }
}
