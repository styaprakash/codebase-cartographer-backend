package com.codebasecartographer.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.codebasecartographer.api.enums.RepositoryStatus;

import jakarta.persistence.EnumType;
import jakarta.persistence.PrePersist;

@Entity

@Table(name = "repositories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class Repository {
    @Id
    @GeneratedValue(strategy=GenerationType.UUID) // Auto-generate UUID for PK
    private String id;

    // Owner of this repo — all queries scoped by this. Its a FK from Users entity
    @ManyToOne
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    // GitHub's internal repo ID
    @Column(name="github_repo_id", nullable=false)
    private String githubRepoId;

    // Short repo name e.g. 'my-project'
    @Column(nullable=false)
    private String name;

    // Full name e.g. 'username/my-project'
    @Column(nullable=false)
    private String fullName;

    // Branch that was indexed (usually 'main')
    @Column(nullable=false)
    private String branch;

    // Primary language detected by GitHub
    @Column(nullable=true) // e.g. a repo with only markdown files has no language
    private String language;

    // pending | indexing | indexed | failed
    @Enumerated(EnumType.STRING) // Store this enum in the database as text/string.
    @Column(nullable=false)
    private RepositoryStatus status;

    // Total files found in the repo
    @Min(0)
    @Builder.Default
    @Column(nullable=false)
    private Integer totalFiles = 0;

    // Files successfully processed — shown in progress bar
    @Min(0)
    @Builder.Default
    @Column(nullable=false)
    private Integer indexedFiles = 0;

    // Populated only when status = 'failed'
    @Column(nullable=true, columnDefinition = "TEXT")
    private String errorMessage;

    // When indexing completed
    @Column(nullable = true)
    private LocalDateTime indexedAt;

    // When user first imported this repo
    @Column(nullable=false, updatable=false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = RepositoryStatus.PENDING; // default status on creation
        }
    }
}
