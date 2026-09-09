package com.codebasecartographer.api.grpc.registry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.codebasecartographer.api.grpc.proto.ServerMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory registry of active Local Compute Agent connections.
 *
 * This is NOT persisted. If the server restarts, all connections are lost
 * and workers must reconnect (which the CLI handles automatically).
 *
 * Thread-safe: all operations use ConcurrentHashMap.
 */
@Slf4j
@Component
public class WorkerRegistry {

    private static final long STALE_THRESHOLD_SECONDS = 90;

    // Primary index: workerId → active connection
    private final ConcurrentHashMap<String, WorkerConnection> connections = new ConcurrentHashMap<>();

    // ── Registration ─────────────────────────────────────────────

    /**
     * Registers a worker's active gRPC connection.
     * If a connection already exists for this workerId (duplicate connection),
     * the old stream is closed and replaced (last-writer-wins).
     */
    public void register(String workerId, String userId, io.grpc.stub.StreamObserver<ServerMessage> stream) {
        WorkerConnection existing = connections.get(workerId);
        if (existing != null) {
            log.warn("[WorkerRegistry] Closing duplicate connection for worker {}", workerId);
            try {
                existing.getStream().onCompleted();
            } catch (Exception e) {
                log.debug("[WorkerRegistry] Error closing old stream for {}: {}", workerId, e.getMessage());
            }
        }

        WorkerConnection conn = new WorkerConnection(workerId, userId, stream);
        connections.put(workerId, conn);
        log.info("[WorkerRegistry] Worker {} registered (user: {}) — {} active workers",
                workerId, userId, connections.size());
    }

    /**
     * Removes a worker's connection from the registry.
     * Called when the gRPC stream closes (graceful or error).
     */
    public void unregister(String workerId) {
        WorkerConnection removed = connections.remove(workerId);
        if (removed != null) {
            log.info("[WorkerRegistry] Worker {} unregistered — {} active workers",
                    workerId, connections.size());
        }
    }

    // ── Lookups ──────────────────────────────────────────────────

    public Optional<WorkerConnection> getConnection(String workerId) {
        return Optional.ofNullable(connections.get(workerId));
    }

    /**
     * Finds an active connection for the given user.
     * If the user has multiple workers, returns any one (MVP behavior).
     */
    public Optional<WorkerConnection> getConnectionForUser(String userId) {
        return connections.values().stream()
                .filter(conn -> conn.getUserId().equals(userId))
                .findFirst();
    }

    /**
     * Returns all active connections for a user.
     */
    public Collection<WorkerConnection> getConnectionsForUser(String userId) {
        return connections.values().stream()
                .filter(conn -> conn.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    public boolean isOnline(String workerId) {
        return connections.containsKey(workerId);
    }

    public int getActiveCount() {
        return connections.size();
    }

    // ── Messaging ────────────────────────────────────────────────

    /**
     * Sends a message to a specific worker through its active gRPC stream.
     * Returns false if the worker is not connected or the send fails.
     */
    public boolean sendToWorker(String workerId, ServerMessage message) {
        WorkerConnection conn = connections.get(workerId);
        if (conn == null) {
            log.warn("[WorkerRegistry] Cannot send to worker {} — not connected", workerId);
            return false;
        }
        try {
            conn.getStream().onNext(message);
            return true;
        } catch (Exception e) {
            log.error("[WorkerRegistry] Failed to send to worker {}: {}", workerId, e.getMessage());
            unregister(workerId);
            return false;
        }
    }

    // ── Heartbeat ────────────────────────────────────────────────

    public void recordHeartbeat(String workerId) {
        WorkerConnection conn = connections.get(workerId);
        if (conn != null) {
            conn.updateHeartbeat();
        }
    }

    // ── Stale worker detection ───────────────────────────────────

    /**
     * Runs every 60 seconds. Closes connections that haven't sent
     * a heartbeat within the threshold (90 seconds).
     */
    @Scheduled(fixedRate = 60_000)
    public void evictStaleWorkers() {
        Instant cutoff = Instant.now().minus(STALE_THRESHOLD_SECONDS, ChronoUnit.SECONDS);

        connections.forEach((workerId, conn) -> {
            if (conn.getLastHeartbeatAt().isBefore(cutoff)) {
                log.warn("[WorkerRegistry] Evicting stale worker {} (last heartbeat: {})",
                        workerId, conn.getLastHeartbeatAt());
                try {
                    conn.getStream().onCompleted();
                } catch (Exception e) {
                    log.debug("[WorkerRegistry] Error closing stale stream: {}", e.getMessage());
                }
                connections.remove(workerId);
            }
        });
    }
}
