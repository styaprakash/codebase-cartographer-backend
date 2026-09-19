package com.codebasecartographer.api.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.entity.LocalJob;
import com.codebasecartographer.api.enums.LocalJobStatus;
import com.codebasecartographer.api.repository.LocalJobRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LocalJobTimeoutService {

    private final LocalJobRepository localJobRepository;

    public LocalJobTimeoutService(LocalJobRepository localJobRepository) {
        this.localJobRepository = localJobRepository;
    }

    /**
     * Runs every 15 seconds. Fails jobs that have been in DISPATCHED state for over 30 seconds.
     */
    @Scheduled(fixedRate = 15000)
    @Transactional
    public void checkDispatchTimeouts() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(30);
        List<LocalJob> staleJobs = localJobRepository.findByStatusAndDispatchedAtBefore(LocalJobStatus.DISPATCHED, cutoff);
        
        for (LocalJob job : staleJobs) {
            log.warn("Job {} timed out waiting for worker {} to accept.", job.getId(), job.getWorkerId());
            job.setStatus(LocalJobStatus.FAILED);
            job.setError("Worker did not respond within the timeout period.");
            job.setUpdatedAt(LocalDateTime.now());
            localJobRepository.save(job);
        }
    }
}
