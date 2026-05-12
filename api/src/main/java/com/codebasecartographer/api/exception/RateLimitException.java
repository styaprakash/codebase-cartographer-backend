package com.codebasecartographer.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitException extends RuntimeException {
    public RateLimitException(String userId, int limit) {
        super(String.format("Daily query limit of %d reached for user: %s. Resets at midnight.", limit, userId));
    }
}
