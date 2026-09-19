package com.codebasecartographer.api.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codebasecartographer.api.entity.LocalJob;
import com.codebasecartographer.api.enums.LocalJobStatus;

@Repository
public interface LocalJobRepository extends JpaRepository<LocalJob, String> {

    List<LocalJob> findByWorkerIdAndStatusIn(String workerId, List<LocalJobStatus> statuses);

    List<LocalJob> findByStatusAndDispatchedAtBefore(LocalJobStatus status, LocalDateTime before);
}
