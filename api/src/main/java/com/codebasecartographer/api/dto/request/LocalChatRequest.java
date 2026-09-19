package com.codebasecartographer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalChatRequest {
    @NotBlank(message = "Model is required")
    private String model;
    
    @NotBlank(message = "Prompt is required")
    private String prompt;
}
