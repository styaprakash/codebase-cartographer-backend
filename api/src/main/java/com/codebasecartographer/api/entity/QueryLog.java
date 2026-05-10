package com.codebasecartographer.api.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "query_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class QueryLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // Who asked the question
    @ManyToOne
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    // Which repo was being queried
    @ManyToOne
    @JoinColumn(name = "repo_id", nullable = false) //use snake-case as JPA auto-converts Java camelCase → snake_case for fields
    private Repository repository;

    // The user's original question
    @Column(columnDefinition = "TEXT", nullable = false) 
    private String question;

    // The full AI answer (stored after stream completes)
    @Column(columnDefinition = "TEXT", nullable = false) 
    private String answer;

    // Array of chunk IDs that were used to generate the answer
    @Column(columnDefinition = "TEXT", nullable = true)
    private String sourceChunks;

    // Total tokens consumed - used for cost tracking
    @Builder.Default
    @Column(nullable=false, updatable=true)
    private Integer tokensUsed = 0;

    // When the query was made
    @Column(nullable=false, updatable=false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
