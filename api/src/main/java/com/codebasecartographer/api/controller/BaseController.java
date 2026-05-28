package com.codebasecartographer.api.controller;

import org.springframework.security.core.context.SecurityContextHolder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseController {

    protected String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            log.error("No authentication found in security context");
            throw new IllegalStateException("No authentication found in security context");
        }
        return (String) auth.getPrincipal();
    }
}