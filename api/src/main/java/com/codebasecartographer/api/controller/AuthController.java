package com.codebasecartographer.api.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codebasecartographer.api.dto.request.AuthCallbackRequest;
import com.codebasecartographer.api.dto.response.AuthResponse;
import com.codebasecartographer.api.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


//It will not extends the BaseController, but these endpoints are public. This is the 1st time user is logging in
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    //constructor injection
    public AuthController(AuthService authService){
        this.authService = authService;
    }

    // ── POST /api/auth/callback ────────────────────────────────
    // Called by NextAuth after GitHub OAuth completes
    @PostMapping("/callback")
    public ResponseEntity<AuthResponse> callback(@Valid @RequestBody AuthCallbackRequest reqeust) {
        AuthResponse response = authService.handleCallback(reqeust);
        
        // 200 OK + token + user data
        return ResponseEntity.ok(response);
    }
    
    // ── POST /api/auth/logout ─────────────────────────────────────
    // JWT is stateless — we can't invalidate it server-side
    // Frontend just deletes the token from session
    // This endpoint exists for completeness + future blacklisting
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // TODO Week 7: add token to Dragonfly blacklist
        // For now: frontend handles logout by clearing session
        return ResponseEntity.noContent().build();
    }
    

}
