package com.codebasecartographer.api.dto.request;

public record EmbeddingRequest(String model,String input, Integer dimensions) {}
