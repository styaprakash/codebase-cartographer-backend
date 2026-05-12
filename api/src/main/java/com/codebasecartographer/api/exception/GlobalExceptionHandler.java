package com.codebasecartographer.api.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // 404 Not Found exception
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex){
        // ProblemDetail.forStatusAndDetail() creates the RFC 7807 body
        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        
        problem.setTitle("Resource not found");

        // Doesn't need to be a real URL — just a unique identifier
        problem.setType(URI.create("https://codebasecartographer.com/errors/not-found"));

        // problem.setStatus(404);

        problem.setProperty("timestamp", LocalDateTime.now());  
        

        return problem;
    }
    
    // 429 Rate Limit
    @ExceptionHandler(RateLimitException.class)
    public ProblemDetail handleRateLimit(RateLimitException ex){
        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());

        problem.setTitle("Query Limit reached!");
        problem.setType(URI.create("https://codebasecartographer.com/errors/rate-limit"));
        problem.setProperty("timestamp", LocalDateTime.now());
        problem.setProperty("limit", 20);
        problem.setProperty("resetAt", "midnight IST");

        return problem;
    }

    // ── 400 Bad Request ───────────────────────────────────────────
    // Thrown when request body is missing required fields
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());

        problem.setTitle("Invalid Request");
        problem.setType(URI.create("https://codebasecartographer.com/errors/bad-request"));
        problem.setProperty("timestamp", LocalDateTime.now());

        return problem;
    }

    // ── 500 Internal Server Error ─────────────────────────────────
    // Safety net — catches anything unhandled
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                               "Something went wrong. Please try again.");

        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://codebasecartographer.com/errors/internal"));
        problem.setProperty("timestamp", LocalDateTime.now());

        return problem;
    }
}
