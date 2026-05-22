package com.codebasecartographer.api.service;

import org.springframework.stereotype.Service;

import com.codebasecartographer.api.dto.response.AuthResponse;
import com.codebasecartographer.api.dto.request.AuthCallbackRequest;
import com.codebasecartographer.api.dto.response.UserResponse;

@Service
public class AuthService {
    private final UserService userService;
    private final JwtService jwtService;

    //constructor injection
    public AuthService(UserService userService, JwtService jwtService){
        this.userService = userService;
        this.jwtService = jwtService;
    }

    // Called when NextAuth completes GitHub OAuth
    // Receives GitHub user data
    // Returns JWT token + user info
    public AuthResponse handleCallback(AuthCallbackRequest request){
        //check if the user is already exist or not
        // -> existing user: update their access token
        // -> new user: create with all GitHub data
        UserResponse user = userService.findOrCreateUser(
            request.getGithubId(),
            request.getName(),
            request.getEmail(),
            request.getAccessToken()
        );

        // Generate JWT token with userId as subject. Token contains: userId, issuedAt, expiration
        String token = jwtService.generateToken(user.getId());

        return AuthResponse.builder()
                    .token(token)  // token for API calls
                    .user(user) //user data for immediate display
                    .build();
    }
}
