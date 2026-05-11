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
public class QueryRequest {
    @NotBlank(message="Question can't be blank!") //@NotNull would allow an empty string like " ".
    private String question;
}
