package com.codebasecartographer.api.dto.response;

import java.util.List;

public record ChatMessage(
    String id,
    String role,
    String content,
    List<String> citations,
    String timestamp
) {}
