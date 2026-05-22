package com.codebasecartographer.api.controller;

import org.springframework.security.core.context.SecurityContextHolder;

// Just shared helper methods for all controllers
public abstract class BaseController {

    // Every controller that extends this gets this method for free
    // Reads userId that JwtAuthFilter stored in SecurityContext
    protected String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authentication found in security context");
        }
        return (String) auth.getPrincipal();
    }
}