package com.codebasecartographer.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.entity.CodeChunk;

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