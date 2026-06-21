package com.codebasecartographer.api.dto;

import java.util.List;

public record IncrementalJobPayload(
    String repoId,
    List<String> added,
    List<String> modified,
    List<String> removed
) {}
