package com.codebasecartographer.api.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// @Entity tells JPA: "This class = a database table", otherwise, JPA ignores the class entirely.
@Entity

@Table(name = "users") // Optional: specify the table name, otherwise it defaults to the class name (User).
@Data               // getters, setters, toString, equals, hashCode
@Builder            // User.builder().name("x").email("y").build()
@NoArgsConstructor  // new User() — required by JPA
@AllArgsConstructor // new User(id, name, email...) — required by @Builder

public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Auto-generate a UUID for the primary key.
    private String id;

    //github_id is teh unique identifier for the user in github and it can't be null
    @Column(nullable = false, unique = true)
    private String githubId;

    //Display name from GitHub profile
    @Column(nullable = false)
    private String name;

    //Email from GitHub — used for account recovery
    @Column(nullable = false, unique = true)
    private String email;

    // GitHub OAuth token — encrypted at rest, never in API responses
    @Column(name = "access_token", columnDefinition = "TEXT", nullable = true) // Use TEXT for longer tokens, allow null if not connected
    private String accessToken;
    
    // Tracks daily query usage — starts at 0
    @Builder.Default
    @Column(nullable = false)
    private Integer dailyQueryCount = 0;

    //When the daily count last reset — used to determine when to reset the count back to 0
    private LocalDateTime queryResetAt;

    // Account creation time: can't be null and never changes after creation
    @Column(nullable = false, updatable=false)
    private LocalDateTime createdAt;

    // @PrePersist = runs automatically just before saving to DB for first time
    // This sets createdAt so you never forget to set it manually
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}