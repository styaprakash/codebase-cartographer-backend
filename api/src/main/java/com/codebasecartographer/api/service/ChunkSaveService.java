package com.codebasecartographer.api.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.entity.CodeChunk;
import com.codebasecartographer.api.repository.CodeChunkRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChunkSaveService {

    private final CodeChunkRepository codeChunkRepository;

    public ChunkSaveService(CodeChunkRepository codeChunkRepository) {
        this.codeChunkRepository = codeChunkRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveChunks(List<CodeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("saveChunks called with empty or null list — skipping");
            return;
        }
        try {
            codeChunkRepository.saveAll(chunks);
            log.info("Successfully saved batch of {} chunks to database", chunks.size());
        } catch (Exception e) {
            log.error("Failed to save chunks to database", e);
            throw new RuntimeException("DB Save Failed for " + chunks.size() + " chunks", e);
        }
    }
}
