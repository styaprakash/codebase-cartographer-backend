package com.codebasecartographer.api.grpc.interceptor;

import org.springframework.stereotype.Component;

import com.codebasecartographer.api.service.JwtService;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/**
 * gRPC server interceptor that authenticates incoming connections
 * using the same JWT tokens as the REST API.
 *
 * The Rust CLI sends the token as gRPC metadata:
 *   authorization: Bearer <jwt>
 *
 * This interceptor validates the token at stream establishment time.
 * The authenticated userId is stored in the gRPC Context and can be
 * retrieved by the stream handler via USER_ID_CTX_KEY.
 */
@Slf4j
@GrpcGlobalServerInterceptor
public class GrpcJwtInterceptor implements ServerInterceptor {

    // Context key for propagating the authenticated userId to stream handlers
    public static final Context.Key<String> USER_ID_CTX_KEY = Context.key("userId");

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final JwtService jwtService;

    public GrpcJwtInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String authHeader = headers.get(AUTHORIZATION_KEY);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[gRPC Auth] Missing or malformed authorization header");
            call.close(Status.UNAUTHENTICATED
                    .withDescription("Missing or invalid authorization metadata"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String token = authHeader.substring(7);

        if (!jwtService.validateToken(token)) {
            log.warn("[gRPC Auth] Invalid or expired JWT");
            call.close(Status.UNAUTHENTICATED
                    .withDescription("Invalid or expired token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String userId = jwtService.extractUserId(token);
        log.debug("[gRPC Auth] Authenticated user: {}", userId);

        // Store userId in gRPC Context — available to all downstream handlers
        Context ctx = Context.current().withValue(USER_ID_CTX_KEY, userId);
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
