package com.codebasecartographer.api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.entity.User;

// @Repository tells Spring: "This is a database access component"
// Spring creates ONE instance of this and manages it
@Repository

// JpaRepository<User, String> means:
// → This repository works with the User entity
// → The primary key (id) is of type String (UUID)
// By extending JpaRepository, you get 15+ free methods instantly
public interface UserRepository extends JpaRepository<User, String> {

    // ── Custom Finder Methods ────────────────────────────────────
    // Spring Data reads the METHOD NAME and generates SQL automatically
    // No SQL needed. The method name IS the query.

    // findBy + GithubId → SELECT * FROM users WHERE github_id = ?
    // Optional<> means: might return a user, might return empty
    // Never returns null — safer than null checks
    Optional<User> findByGithubId(String githubId);

    // findBy + Email → SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);

    // existsBy + GithubId → SELECT COUNT(*) > 0 FROM users WHERE github_id = ?
    // Returns true/false — used to check if user already exists before creating
    boolean existsByGithubId(String githubId);

    // ── Custom JPQL Query ────────────────────────────────────────
    // When method name gets too complex, write JPQL directly
    // JPQL = Java Persistence Query Language
    // Uses CLASS names (User) and FIELD names (dailyQueryCount)
    // NOT table names (users) or column names (daily_query_count)

    // @Modifying = this query changes data (UPDATE/DELETE)
    // @Transactional = wrap in a transaction — if it fails, rollback
    // @Query = write the query manually
    // :userId = named parameter, matched by @Param("userId")
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.dailyQueryCount = 0, u.queryResetAt = CURRENT_TIMESTAMP WHERE u.id = :userId")
    void resetDailyQueryCount(@Param("userId") String userId);

    // Increment query count by 1 for a specific user
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.dailyQueryCount = u.dailyQueryCount + 1 WHERE u.id = :userId")
    void incrementQueryCount(@Param("userId") String userId);
}