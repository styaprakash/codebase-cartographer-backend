package com.codebasecartographer.api.dto.response;

import java.util.List;

public record ChatResponse(
    String answer,
    List<String> citations
) {}
