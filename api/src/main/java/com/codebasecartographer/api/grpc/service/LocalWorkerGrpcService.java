package com.codebasecartographer.api.grpc.service;

import com.codebasecartographer.api.grpc.proto.LocalWorkerServiceGrpc;
import com.codebasecartographer.api.grpc.proto.ServerAck;
import com.codebasecartographer.api.grpc.proto.ServerMessage;
import com.codebasecartographer.api.grpc.proto.WorkerHello;
import com.codebasecartographer.api.grpc.proto.WorkerMessage;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC service handling the persistent bidirectional stream
 * with a Local Compute Agent (Rust CLI).
 *
 * Milestone 1: Only handles WorkerHello → ServerAck.
 * Future milestones will add Heartbeat, Job dispatch, streaming, etc.
 */
@Slf4j
@GrpcService
public class LocalWorkerGrpcService extends LocalWorkerServiceGrpc.LocalWorkerServiceImplBase {

    @Override
    public StreamObserver<WorkerMessage> connect(StreamObserver<ServerMessage> responseObserver) {
        log.info("[gRPC] Local worker stream opened");

        return new StreamObserver<>() {

            @Override
            public void onNext(WorkerMessage message) {
                switch (message.getPayloadCase()) {
                    case HELLO -> handleHello(message.getHello(), responseObserver);
                    case PAYLOAD_NOT_SET -> log.warn("[gRPC] Received message with no payload set");
                    default -> log.warn("[gRPC] Received unhandled message type: {}", message.getPayloadCase());
                }
            }

            @Override
            public void onError(Throwable t) {
                // Fires when the CLI disconnects unexpectedly (network loss, crash, kill).
                // In future milestones this will trigger worker cleanup and job failure.
                log.warn("[gRPC] Local worker stream error: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                // Fires when the CLI gracefully closes the stream.
                log.info("[gRPC] Local worker stream closed by client");
                responseObserver.onCompleted();
            }
        };
    }

    // ── Milestone 1: Hello / Ack ────────────────────────────────────

    private void handleHello(WorkerHello hello, StreamObserver<ServerMessage> responseObserver) {
        log.info("[gRPC] Worker hello received — workerId={}, device={}, cliVersion={}",
                hello.getWorkerId(), hello.getDeviceName(), hello.getCliVersion());

        ServerMessage ack = ServerMessage.newBuilder()
                .setAck(ServerAck.newBuilder()
                        .setSuccess(true)
                        .setMessage("Connection established")
                        .build())
                .build();

        responseObserver.onNext(ack);
        log.info("[gRPC] Sent acknowledgement to worker {}", hello.getWorkerId());
    }
}
