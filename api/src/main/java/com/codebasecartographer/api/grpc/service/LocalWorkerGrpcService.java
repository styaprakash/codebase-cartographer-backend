package com.codebasecartographer.api.grpc.service;

import java.time.LocalDateTime;

import com.codebasecartographer.api.entity.LocalWorker;
import com.codebasecartographer.api.grpc.interceptor.GrpcJwtInterceptor;
import com.codebasecartographer.api.grpc.proto.Heartbeat;
import com.codebasecartographer.api.grpc.proto.LocalWorkerServiceGrpc;
import com.codebasecartographer.api.grpc.proto.RegisterWorker;
import com.codebasecartographer.api.grpc.proto.ServerAck;
import com.codebasecartographer.api.grpc.proto.ServerMessage;
import com.codebasecartographer.api.grpc.proto.WorkerHello;
import com.codebasecartographer.api.grpc.proto.WorkerMessage;
import com.codebasecartographer.api.grpc.proto.JobAccepted;
import com.codebasecartographer.api.grpc.proto.JobRejected;
import com.codebasecartographer.api.grpc.registry.WorkerRegistry;
import com.codebasecartographer.api.repository.LocalWorkerRepository;
import com.codebasecartographer.api.service.LocalJobService;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC service handling the persistent bidirectional stream
 * with a Local Compute Agent (Rust CLI).
 *
 * Lifecycle:
 * 1. CLI opens stream (authenticated via GrpcJwtInterceptor)
 * 2. CLI sends RegisterWorker → server validates ownership, persists worker, registers connection
 * 3. CLI sends periodic Heartbeat → server updates lastSeen
 * 4. Stream closes → server unregisters connection, marks worker offline
 */
@Slf4j
@GrpcService
public class LocalWorkerGrpcService extends LocalWorkerServiceGrpc.LocalWorkerServiceImplBase {

    private final WorkerRegistry workerRegistry;
    private final LocalWorkerRepository localWorkerRepository;
    private final LocalJobService localJobService;

    public LocalWorkerGrpcService(WorkerRegistry workerRegistry,
                                   LocalWorkerRepository localWorkerRepository,
                                   LocalJobService localJobService) {
        this.workerRegistry = workerRegistry;
        this.localWorkerRepository = localWorkerRepository;
        this.localJobService = localJobService;
    }

    @Override
    public StreamObserver<WorkerMessage> connect(StreamObserver<ServerMessage> responseObserver) {
        // userId was set by GrpcJwtInterceptor — guaranteed non-null here
        String userId = GrpcJwtInterceptor.USER_ID_CTX_KEY.get();
        log.info("[gRPC] Authenticated stream opened for user: {}", userId);

        return new StreamObserver<>() {

            // Tracks the workerId once registration is complete
            private volatile String registeredWorkerId = null;

            @Override
            public void onNext(WorkerMessage message) {
                switch (message.getPayloadCase()) {
                    case REGISTER -> handleRegister(message.getRegister(), responseObserver);
                    case HELLO -> handleHello(message.getHello(), responseObserver);
                    case HEARTBEAT -> handleHeartbeat(message.getHeartbeat());
                    case JOB_ACCEPTED -> handleJobAccepted(message.getJobAccepted());
                    case JOB_REJECTED -> handleJobRejected(message.getJobRejected());
                    case PAYLOAD_NOT_SET -> log.warn("[gRPC] Received message with no payload");
                    default -> log.warn("[gRPC] Unhandled message type: {}", message.getPayloadCase());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("[gRPC] Stream error for user {}: {}", userId, t.getMessage());
                cleanup();
            }

            @Override
            public void onCompleted() {
                log.info("[gRPC] Stream closed by client (user: {})", userId);
                cleanup();
                responseObserver.onCompleted();
            }

            // ── Message handlers ─────────────────────────────────

            private void handleRegister(RegisterWorker register, StreamObserver<ServerMessage> out) {
                String workerId = register.getWorkerId();
                log.info("[gRPC] RegisterWorker — workerId={}, device={}, cli={}, models={}",
                        workerId, register.getDeviceName(), register.getCliVersion(),
                        register.getAvailableModelsList());

                // Persist or update worker identity in database
                LocalWorker worker = localWorkerRepository.findByWorkerId(workerId)
                        .map(existing -> {
                            // Verify ownership: the authenticated user must own this worker
                            if (!existing.getUserId().equals(userId)) {
                                log.warn("[gRPC] Worker {} belongs to user {}, but user {} tried to register it",
                                        workerId, existing.getUserId(), userId);
                                sendAck(out, false, "Worker belongs to another user");
                                return null;
                            }
                            // Update metadata on reconnect
                            existing.setDeviceName(register.getDeviceName());
                            existing.setCliVersion(register.getCliVersion());
                            existing.setLastSeenAt(LocalDateTime.now());
                            return localWorkerRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            // First time this worker connects — create identity
                            LocalWorker newWorker = LocalWorker.builder()
                                    .workerId(workerId)
                                    .userId(userId)
                                    .deviceName(register.getDeviceName())
                                    .cliVersion(register.getCliVersion())
                                    .lastSeenAt(LocalDateTime.now())
                                    .build();
                            return localWorkerRepository.save(newWorker);
                        });

                if (worker == null) return; // ownership validation failed

                // Register the active gRPC connection
                registeredWorkerId = workerId;
                workerRegistry.register(workerId, userId, out);

                sendAck(out, true, "Worker registered successfully");
                log.info("[gRPC] Worker {} is now ONLINE", workerId);
            }

            private void handleHello(WorkerHello hello, StreamObserver<ServerMessage> out) {
                // Milestone 1 backward compat — treat as a lightweight register
                log.info("[gRPC] WorkerHello (legacy) — workerId={}, device={}, cli={}",
                        hello.getWorkerId(), hello.getDeviceName(), hello.getCliVersion());
                sendAck(out, true, "Connection established");
            }

            private void handleHeartbeat(Heartbeat heartbeat) {
                if (registeredWorkerId != null) {
                    workerRegistry.recordHeartbeat(registeredWorkerId);
                    // Update lastSeenAt in database periodically
                    localWorkerRepository.findByWorkerId(registeredWorkerId)
                            .ifPresent(worker -> {
                                worker.setLastSeenAt(LocalDateTime.now());
                                localWorkerRepository.save(worker);
                            });
                    log.debug("[gRPC] Heartbeat from worker {}", registeredWorkerId);
                }
            }

            private void handleJobAccepted(JobAccepted jobAccepted) {
                if (registeredWorkerId != null) {
                    localJobService.handleJobAccepted(registeredWorkerId, jobAccepted.getJobId());
                } else {
                    log.warn("[gRPC] Received JobAccepted but worker is not registered");
                }
            }

            private void handleJobRejected(JobRejected jobRejected) {
                if (registeredWorkerId != null) {
                    localJobService.handleJobRejected(registeredWorkerId, jobRejected.getJobId(), jobRejected.getReason());
                } else {
                    log.warn("[gRPC] Received JobRejected but worker is not registered");
                }
            }

            // ── Cleanup on disconnect ────────────────────────────

            private void cleanup() {
                if (registeredWorkerId != null) {
                    workerRegistry.unregister(registeredWorkerId);
                    localJobService.handleWorkerDisconnect(registeredWorkerId);
                    log.info("[gRPC] Worker {} is now OFFLINE", registeredWorkerId);
                }
            }

            // ── Utility ──────────────────────────────────────────

            private void sendAck(StreamObserver<ServerMessage> out, boolean success, String message) {
                out.onNext(ServerMessage.newBuilder()
                        .setAck(ServerAck.newBuilder()
                                .setSuccess(success)
                                .setMessage(message)
                                .build())
                        .build());
            }
        };
    }
}
