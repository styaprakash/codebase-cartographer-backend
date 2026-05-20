package com.codebasecartographer.api.controller;

import org.springframework.security.core.context.SecurityContextHolder;

// Just shared helper methods for all controllers
public abstract class BaseController {

    // Every controller that extends this gets this method for free
    // Reads userId that JwtAuthFilter stored in SecurityContext
    protected String getCurrentUserId() {
        return (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}