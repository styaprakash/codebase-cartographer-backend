package com.codebasecartographer.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.entity.CodeChunk;

@Repository

public interface CodeChunkRepository extends JpaRepository<CodeChunk, String> {
    // findByRepositoryId → get all chunks for a repo
    List<CodeChunk> findByRepository_Id(String repositoryId);   

    // Get all chunks from one specific file in a repo
    List<CodeChunk> findByRepository_IdAndFilePath(String repositoryId, String filePath);

    // Delete all chunks when re-indexing a repo.
    // Uses native query to avoid Hibernate loading entities with pgvector columns,
    // which crashes the PostgreSQL JDBC driver (PSQLException in TypeInfoCache).
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM code_chunks WHERE repo_id = :repoId", nativeQuery = true)
    void deleteChunksByRepoId(@Param("repoId") String repositoryId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM code_chunks WHERE repo_id = :repoId AND file_path IN :filePaths", nativeQuery = true)
    void deleteChunksByRepoIdAndFilePathIn(@Param("repoId") String repositoryId, @Param("filePaths") List<String> filePaths);

    // Fetch distinct file paths for a repo — used by the file tree endpoint.
    // Uses native query to avoid Hibernate loading entities with pgvector embedding
    // columns, which crashes the PostgreSQL JDBC driver (PSQLException in TypeInfoCache).
    @Query(value = "SELECT DISTINCT file_path FROM code_chunks WHERE repo_id = :repoId ORDER BY file_path", nativeQuery = true)
    List<String> findDistinctFilePathsByRepoId(@Param("repoId") String repoId);

    // countByRepository_Id → how many chunks indexed so far for a repo (used for progress tracking)
    long countByRepository_Id(String repositoryId);

    // If indexing failed then this when some files failed to embed
    List<CodeChunk> findByRepository_IdAndEmbeddingIsNull(String repositoryId);

    long countByRepository_IdAndEmbeddingIsNotNull(String repositoryId);

    long countByRepository_IdAndEmbeddingIsNull(String repositoryId);

    // pgvector cosine similarity search — scoped to one repo
    // The <=> operator is pgvector's cosine distance
    @Query(value = """
        SELECT * FROM code_chunks
        WHERE repo_id = :repoId
        AND embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<CodeChunk> findTopSimilarChunks(
        @Param("repoId") String repoId,
        @Param("queryVector") String queryVector,
        @Param("limit") int limit);

    // Semantic search with similarity threshold and explicit result columns
    @Query(value = """
        SELECT
            c.id,
            c.content,
            c.file_path,
            c.start_line,
            c.end_line,
            1 - (c.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
        FROM code_chunks c
        WHERE c.repo_id = :repoId
            AND 1 - (c.embedding <=> CAST(:queryEmbedding AS vector)) >= :threshold
        ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarChunks(
        @Param("repoId") String repoId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("threshold") double threshold,
        @Param("limit") int limit);
}