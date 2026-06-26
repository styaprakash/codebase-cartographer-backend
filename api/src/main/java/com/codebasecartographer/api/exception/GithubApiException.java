package com.codebasecartographer.api.exception;

public class GithubApiException extends RuntimeException {
    private final int statusCode;

    public GithubApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
