package com.codebasecartographer.api.repository;

import com.codebasecartographer.api.entity.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository

public interface QueryLogRepository extends JpaRepository<QueryLog, String> {
    // findByUserId → get all query logs for a user
    List<QueryLog> findByUser_Id(String userId);

    // findByUserIdAndId → get all  query logs for a specific repo
    List<QueryLog> findByRepository_Id(String repositoryId);

    // Count how many queries user made after a specific time
    // Used for 20/day rate limiting check
    // After = greater than in Spring Data
    long countByUser_IdAndCreatedAtAfter(String userId, LocalDateTime after);

    //deleteByUserId → delete all query logs for a user (e.g. when deleting account)
    @Modifying 
    @Transactional
    void deleteByUser_Id(String userId);
}