package com.codebasecartographer.api.dto.response;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {
    private String answer; // The generated answer from the LLM

    // The list of files the LLM used to formulate the answer (citations)
    @Builder.Default
    private List<String> sourceFiles = new ArrayList<>();

    //Number of token used
    private Integer tokensUsed;
}
