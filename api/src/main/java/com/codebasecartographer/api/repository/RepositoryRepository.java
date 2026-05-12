package com.codebasecartographer.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.enums.RepositoryStatus;

// @Repository
@org.springframework.stereotype.Repository


public interface RepositoryRepository extends JpaRepository<Repository, String> {
    // user is a field in Repository entity
    // id is a field in User entity
    // Spring traverses: repository.user.id → WHERE user_id = ?
    List<Repository> findByUserId(String userId);

    // Get ONE repo — scoped to user (security: user can't access others' repos)
    // user_id = ? AND id = ?
    Optional<Repository> findByUserIdAndId(String userId, String repoId);

    // Get all repos with a specific status e.g. INDEXING
    // WHERE status = 'INDEXING'
    List<Repository> findByStatus(RepositoryStatus status);

    // Check if user already imported this GitHub repo
    // WHERE user_id = ? AND github_repo_id = ?
    Optional<Repository> findByUserIdAndGithubRepoId(String userId, String githubRepoId);

    boolean existsByUserIdAndGithubRepoId(String userId, String githubRepoId);

}