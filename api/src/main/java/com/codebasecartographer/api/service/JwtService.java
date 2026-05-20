package com.codebasecartographer.api.service;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.codebasecartographer.api.config.JwtConfig;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final JwtConfig jwtConfig;

    //Constructor injection
    public JwtService(JwtConfig jwtConfig){
        this.jwtConfig = jwtConfig;
    }

    // Called after GitHub OAuth — creates token for the user
    // Token contains userId — extracted on every request
    public String generateToken(String userId){
        return Jwts.builder()
            .subject(userId) //who this token is for
            .issuedAt(new Date()) //when token was created
            .expiration(new Date( //when token will get expired
                System.currentTimeMillis() + jwtConfig.getExpiration()
            ))
            .signWith(getSigningKey()) //sign with out secret key
            .compact(); //build the final JWT string
    }

    // ── Extract UserId ────────────────────────────────────────────
    // Called on every request by JwtAuthFilter
    // Reads userId from token payload
    public String extractUserId(String token){
        return parseClaims(token).getId();
    }

    // Checks:
    // 1. Signature is valid (not tampered)
    // 2. Token is not expired
    // Returns true = valid, false = invalid/expired
    public boolean validateToken(String token){
        try{
            parseClaims(token);
            return true;
        }catch(JwtException | IllegalArgumentException e){
            return false;
        }
    }

    // Decodes the JWT and returns the payload
    // Throws exception if token is invalid or expired
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Converts secret string → cryptographic key
    // HMAC-SHA256 requires at least 256-bit key
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes());
    }
}
