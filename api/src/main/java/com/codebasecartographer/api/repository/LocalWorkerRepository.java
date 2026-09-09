package com.codebasecartographer.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codebasecartographer.api.entity.LocalWorker;

@Repository
public interface LocalWorkerRepository extends JpaRepository<LocalWorker, String> {

    Optional<LocalWorker> findByWorkerId(String workerId);

    Optional<LocalWorker> findByWorkerIdAndUserId(String workerId, String userId);

    List<LocalWorker> findByUserId(String userId);
}
