package com.codebasecartographer.api.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.entity.LocalJob;
import com.codebasecartographer.api.enums.LocalJobStatus;
import com.codebasecartographer.api.exception.ServiceUnavailableException;
import com.codebasecartographer.api.grpc.proto.JobAssignment;
import com.codebasecartographer.api.grpc.proto.ServerMessage;
import com.codebasecartographer.api.grpc.registry.WorkerConnection;
import com.codebasecartographer.api.grpc.registry.WorkerRegistry;
import com.codebasecartographer.api.repository.LocalJobRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LocalJobService {

    private final LocalJobRepository localJobRepository;
    private final WorkerRegistry workerRegistry;

    public LocalJobService(LocalJobRepository localJobRepository, WorkerRegistry workerRegistry) {
        this.localJobRepository = localJobRepository;
        this.workerRegistry = workerRegistry;
    }

    @Transactional
    public LocalJob submitJob(String userId, String model, String prompt) {
        // 1. Create Job in PENDING state
        LocalJob job = LocalJob.builder()
                .userId(userId)
                .model(model)
                .prompt(prompt)
                .status(LocalJobStatus.PENDING)
                .build();
        
        job = localJobRepository.save(job);
        
        // 2. Find online worker for user
        WorkerConnection worker = workerRegistry.getConnectionsForUser(userId)
                .stream()
                .max(Comparator.comparing(WorkerConnection::getLastHeartbeatAt))
                .orElse(null);

        if (worker == null) {
            transition(job, LocalJobStatus.PENDING, LocalJobStatus.FAILED, "No connected local compute worker found for user.");
            localJobRepository.save(job);
            throw new ServiceUnavailableException("No connected local compute worker found.");
        }

        job.setWorkerId(worker.getWorkerId());

        // 3. Build JobAssignment protobuf
        ServerMessage assignmentMessage = ServerMessage.newBuilder()
                .setJobAssignment(JobAssignment.newBuilder()
                        .setJobId(job.getId())
                        .setModel(job.getModel())
                        .setPrompt(job.getPrompt())
                        .build())
                .build();

        // 4. Send via WorkerRegistry
        boolean sent = workerRegistry.sendToWorker(worker.getWorkerId(), assignmentMessage);

        if (!sent) {
            transition(job, LocalJobStatus.PENDING, LocalJobStatus.FAILED, "Failed to send assignment to worker.");
            localJobRepository.save(job);
            throw new ServiceUnavailableException("Failed to send assignment to connected worker.");
        }

        // 5. Update status
        transition(job, LocalJobStatus.PENDING, LocalJobStatus.DISPATCHED, null);
        job.setDispatchedAt(LocalDateTime.now());
        
        return localJobRepository.save(job);
    }

    @Transactional
    public void handleJobAccepted(String workerId, String jobId) {
        localJobRepository.findById(jobId).ifPresent(job -> {
            if (validateOwnership(job, workerId)) {
                if (transition(job, LocalJobStatus.DISPATCHED, LocalJobStatus.ACCEPTED, null)) {
                    localJobRepository.save(job);
                }
            }
        });
    }

    @Transactional
    public void handleJobRejected(String workerId, String jobId, String reason) {
        localJobRepository.findById(jobId).ifPresent(job -> {
            if (validateOwnership(job, workerId)) {
                if (transition(job, LocalJobStatus.DISPATCHED, LocalJobStatus.REJECTED, reason)) {
                    localJobRepository.save(job);
                }
            }
        });
    }

    @Transactional
    public void handleWorkerDisconnect(String workerId) {
        List<LocalJob> activeJobs = localJobRepository.findByWorkerIdAndStatusIn(workerId, 
                List.of(LocalJobStatus.DISPATCHED, LocalJobStatus.ACCEPTED, LocalJobStatus.STREAMING));
        
        for (LocalJob job : activeJobs) {
            transition(job, job.getStatus(), LocalJobStatus.FAILED, "Worker disconnected.");
            localJobRepository.save(job);
        }
    }
    
    // Internal state transition method ensuring terminal states are safe and progression is valid
    private boolean transition(LocalJob job, LocalJobStatus expectedFrom, LocalJobStatus to, String errorReason) {
        LocalJobStatus current = job.getStatus();
        
        // Terminal states are absorbing
        if (isTerminal(current)) {
            log.warn("Attempt to transition job {} from terminal state {} to {}", job.getId(), current, to);
            return false;
        }

        if (current != expectedFrom) {
            log.warn("Invalid job state transition for {}: expected {} but was {}", job.getId(), expectedFrom, current);
            return false;
        }

        job.setStatus(to);
        job.setUpdatedAt(LocalDateTime.now());

        if (errorReason != null && (to == LocalJobStatus.FAILED || to == LocalJobStatus.REJECTED)) {
            job.setError(errorReason);
        }

        log.info("Job {} transitioned to {}", job.getId(), to);
        return true;
    }

    private boolean validateOwnership(LocalJob job, String workerId) {
        if (!workerId.equals(job.getWorkerId())) {
            log.warn("Worker {} attempted to update job {} belonging to worker {}", workerId, job.getId(), job.getWorkerId());
            return false;
        }
        return true;
    }

    private boolean isTerminal(LocalJobStatus status) {
        return status == LocalJobStatus.COMPLETED || 
               status == LocalJobStatus.FAILED || 
               status == LocalJobStatus.REJECTED || 
               status == LocalJobStatus.CANCELLED;
    }
}
