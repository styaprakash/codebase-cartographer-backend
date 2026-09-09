package com.codebasecartographer.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codebasecartographer.api.entity.CliSession;

@Repository
public interface CliSessionRepository extends JpaRepository<CliSession, String> {

    Optional<CliSession> findByTokenHash(String tokenHash);

    List<CliSession> findByUserIdAndRevokedFalse(String userId);

    Optional<CliSession> findByIdAndUserId(String id, String userId);
}
