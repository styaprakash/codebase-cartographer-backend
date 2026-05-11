package com.codebasecartographer.api.repository;

import com.codebasecartographer.api.entity.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

@Repository

public interface CodeChunkRepository extends JpaRepository<CodeChunk, String> {
    // findByRepositoryId → get all chunks for a repo
    List<CodeChunk> findByRepository_Id(String repositoryId);   

    // Get all chunks from one specific file in a repo
    List<CodeChunk> findByRepository_IdAndFilePath(String repositoryId, String filePath);

    // Delete all chunks when re-indexing a repo
    @Modifying 
    @Transactional
    void deleteByRepository_Id(String repositoryId);

    // countByRepositoryId → how many chunks indexed so far for a repo (used for progress tracking)
    long countByRepository_Id(String repositoryId);
}