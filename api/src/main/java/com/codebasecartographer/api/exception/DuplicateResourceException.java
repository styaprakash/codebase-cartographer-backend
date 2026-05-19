package com.codebasecartographer.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String resourceName, String resourceIdentifier) {
        super(String.format("%s already imported: %s", resourceName, resourceIdentifier));
    }
}
