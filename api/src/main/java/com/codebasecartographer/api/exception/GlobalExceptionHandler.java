package com.codebasecartographer.api.exception;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 404 Not Found exception
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex){
        log.warn("Resource not found: {}", ex.getMessage());
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
        log.warn("Rate limit exceeded: {}", ex.getMessage());
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
        log.warn("Invalid request argument: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());

        problem.setTitle("Invalid Request");
        problem.setType(URI.create("https://codebasecartographer.com/errors/bad-request"));
        problem.setProperty("timestamp", LocalDateTime.now());

        return problem;
    }

    // Handle custom BadRequestException
    @ExceptionHandler(com.codebasecartographer.api.exception.BadRequestException.class)
    public ProblemDetail handleBadRequestCustom(com.codebasecartographer.api.exception.BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());

        problem.setTitle("Invalid Request");
        problem.setType(URI.create("https://codebasecartographer.com/errors/bad-request"));
        problem.setProperty("timestamp", LocalDateTime.now());

        return problem;
    }

    // 409 Conflict — e.g. duplicate indexing request
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex){
        log.warn("Conflict: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());

        problem.setTitle("Conflict");
        problem.setType(URI.create("https://codebasecartographer.com/errors/conflict"));
        problem.setProperty("timestamp", LocalDateTime.now());

        return problem;
    }

    // Handle duplicate resource errors specifically
    @ExceptionHandler(com.codebasecartographer.api.exception.DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(com.codebasecartographer.api.exception.DuplicateResourceException ex){
        log.warn("Duplicate resource: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());

        problem.setTitle("Duplicate Resource");
        problem.setType(URI.create("https://codebasecartographer.com/errors/duplicate"));
        problem.setProperty("timestamp", LocalDateTime.now());

        return problem;
    }

    // Validation errors from @Valid annotated @RequestBody
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex){
        log.warn("Validation failed for request: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");

        problem.setTitle("Validation failed");
        problem.setType(URI.create("https://codebasecartographer.com/errors/bad-request"));
        problem.setProperty("timestamp", LocalDateTime.now());

        List<Map<String,String>> errors = ex.getBindingResult().getFieldErrors()
            .stream()
            .map(err -> Map.of("field", err.getField(), "message", err.getDefaultMessage()))
            .collect(Collectors.toList());

        problem.setProperty("errors", errors);
        return problem;
    }

    // Malformed JSON / unreadable message
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex){
        log.warn("Malformed request body: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request content.");

        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://codebasecartographer.com/errors/bad-request"));
        problem.setProperty("timestamp", LocalDateTime.now());

        return problem;
    }

    // ── 500 Internal Server Error ─────────────────────────────────
    // Safety net — catches anything unhandled
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception occurred", ex);

        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                               "Something went wrong. Please try again.");

        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://codebasecartographer.com/errors/internal"));
        problem.setProperty("timestamp", LocalDateTime.now());

        return problem;
    }
}
