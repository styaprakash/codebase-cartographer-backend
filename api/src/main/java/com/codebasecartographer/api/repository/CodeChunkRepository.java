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

    // countByRepository_Id → how many chunks indexed so far for a repo (used for progress tracking)
    long countByRepository_Id(String repositoryId);

    // If indexing failed then this when some files failed to embed
    List<CodeChunk> findByRepository_IdAndEmbeddingIsNull(String repositoryId);

    long countByRepository_IdAndEmbeddingIsNotNull(String repositoryId);

    long countByRepository_IdAndEmbeddingIsNull(String repositoryId);
}