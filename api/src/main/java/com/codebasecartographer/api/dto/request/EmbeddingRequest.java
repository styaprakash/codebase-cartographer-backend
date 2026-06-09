package com.codebasecartographer.api.dto.request;

import java.util.List;

public record EmbeddingRequest(String model, List<String> input, Integer dimensions) {
}
