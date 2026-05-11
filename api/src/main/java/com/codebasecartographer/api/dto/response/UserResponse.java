package com.codebasecartographer.api.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id; // to make API calls like GET /api/repos?userId=xxx
    // private String githubId; 
    private String name; //display "Welcome, Satya"
    private String email; // show in Settings page
    private Integer dailyQueryCount; // show "14/20 queries used"
    private LocalDateTime queryResetAt; // show "Resets at midnight"
    private LocalDateTime createdAt; //show "Member since..."


    // NO accessToken — NEVER send the OAuth token in a response

    // githubId is an internal backend identifier — it's only used by your server to:
    // ->Find existing users on login
    // ->Prevent duplicate accounts
}
