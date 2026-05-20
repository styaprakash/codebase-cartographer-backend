package com.codebasecartographer.api.filter;

import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.codebasecartographer.api.service.JwtService;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// OncePerRequestFilter = runs exactly once per HTTP request

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    //constructor injectin
    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain filterChain)
        throws ServletException, IOException {
        //Step 1: Read the authorization header
        String authHeader = request.getHeader("Authorization");

        //Step 2: No header or wrong format -> skip auth
        // Request continues without authentication
        // SecurityConfig decides if endpoint needs auth
        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            filterChain.doFilter(request, response);
            return;
        }

        //Step 3: get the token by removing "Bearer " from the header
        String token = authHeader.substring(7);

        // Step 4 — Validate token
        if(!jwtService.validateToken(token)){
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or expired token");
            return;
        }

        // Step5 : Extract userId from token
        String userId = jwtService.extractUserId(token);

        // Step 6 — Set authentication in Spring Security context
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId,        // principal = userId (who they are)
                null,          // credentials = null (no password)
                Collections.emptyList() // authorities = empty (no roles yet)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Step 7 — Continue to controller
        filterChain.doFilter(request, response);
    }
}
