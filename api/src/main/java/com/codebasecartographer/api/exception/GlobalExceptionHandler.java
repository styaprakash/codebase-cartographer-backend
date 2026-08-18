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
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequestCustom(BadRequestException ex) {
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
    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex){
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

    // ── External API Errors ───────────────────────────────────────
    // This handler catches our custom GithubApiException thrown during the Zip download
    // or any other GitHub API interactions. It gracefully maps GitHub's native HTTP status
    // codes (like 400 Bad Request or 404 Not Found) into our standardized ProblemDetail
    // format. This prevents the Spring framework from treating the failure as an unhandled
    // exception, which would sever the SSE stream and return an opaque 500 Internal Server Error.
    @ExceptionHandler(GithubApiException.class)
    public ProblemDetail handleGithubApi(GithubApiException ex){
        log.error("GitHub API error: {}", ex.getMessage());
        
        // Map GitHub's status code to the closest Spring HttpStatus equivalent
        HttpStatus status = HttpStatus.BAD_GATEWAY; // Default to 502 (Bad Gateway) if unknown
        if (ex.getStatusCode() == 400) status = HttpStatus.BAD_REQUEST;
        else if (ex.getStatusCode() == 401 || ex.getStatusCode() == 403) status = HttpStatus.UNAUTHORIZED;
        else if (ex.getStatusCode() == 404) status = HttpStatus.NOT_FOUND;
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle("GitHub Integration Error");
        problem.setType(URI.create("https://codebasecartographer.com/errors/github-api"));
        problem.setProperty("timestamp", LocalDateTime.now());
        // Pass along the original GitHub status code to the frontend for precise debugging
        problem.setProperty("githubStatusCode", ex.getStatusCode());

        return problem;
    }

    // ── 500 Internal Server Error ─────────────────────────────────
    // Safety net — catches anything unhandled
    @ExceptionHandler(Throwable.class)
    public ProblemDetail handleGeneral(Throwable ex) {
        log.error("Unhandled exception caught by GlobalExceptionHandler: ", ex);

        ProblemDetail problem = ProblemDetail
            .forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                               "Something went wrong. Please try again.");

        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://codebasecartographer.com/errors/internal"));
        problem.setProperty("timestamp", LocalDateTime.now());

        // Pass exception message for dev debugging
        if (ex.getMessage() != null) {
            problem.setDetail(ex.getMessage());
        }

        return problem;
    }
}
