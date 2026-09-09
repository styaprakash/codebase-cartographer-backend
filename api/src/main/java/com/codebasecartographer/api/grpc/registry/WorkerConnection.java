package com.codebasecartographer.api.grpc.registry;

import java.time.Instant;

import com.codebasecartographer.api.grpc.proto.ServerMessage;

import io.grpc.stub.StreamObserver;

/**
 * Represents an active gRPC connection to a Local Compute Agent worker.
 * Exists only in memory — not persisted. Created when a worker registers
 * on the bidirectional stream, removed when the stream closes.
 */
public class WorkerConnection {

    private final String workerId;
    private final String userId;
    private final StreamObserver<ServerMessage> stream;
    private final Instant connectedAt;
    private volatile Instant lastHeartbeatAt;

    public WorkerConnection(String workerId, String userId,
                            StreamObserver<ServerMessage> stream) {
        this.workerId = workerId;
        this.userId = userId;
        this.stream = stream;
        this.connectedAt = Instant.now();
        this.lastHeartbeatAt = Instant.now();
    }

    public String getWorkerId() { return workerId; }

    public String getUserId() { return userId; }

    public StreamObserver<ServerMessage> getStream() { return stream; }

    public Instant getConnectedAt() { return connectedAt; }

    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }

    public void updateHeartbeat() { this.lastHeartbeatAt = Instant.now(); }
}
