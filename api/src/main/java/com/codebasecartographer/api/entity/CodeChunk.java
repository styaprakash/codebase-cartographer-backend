package com.codebasecartographer.api.entity;

import java.time.LocalDateTime;

import com.codebasecartographer.api.enums.ChunkType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="code_chunks")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class CodeChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Auto-generate UUID for PK
    private String id;

    // Which repo this chunk belongs to
    @ManyToOne
    @JoinColumn(name = "repo_id", nullable = false) //use snake-case as JPA auto-converts Java camelCase → snake_case for fields
    private Repository repository;

    // Full file path e.g. 'src/auth/login.ts'
    @Column(nullable=false)
    private String filePath;

    // function | class | module | unknown
    @Enumerated(EnumType.STRING) // Store this enum in the database as text/string.
    @Column(nullable=false)
    private ChunkType chunkType;

    //Name of the function or class
    @Column(nullable=false)
    private String chunkName;

    // The actual source code of this chunk
    @Column(columnDefinition = "TEXT", nullable = false) // content can't be null
    private String content;

    // First line of this chunk in the file
    @Column(nullable = false)
    private Integer startLine;

    // Last line of this chunk in the file
    @Column(nullable = false)
    private Integer endLine;

    // NOTE: embedding field added in Week 3 with pgvector setup
    // Column type = VECTOR(1536) — cannot be done with standard JPA

    @Builder.Default
    @Column(nullable=false)
    private Integer aiReferenceCount = 0;

    @Column(nullable=false, updatable=false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
