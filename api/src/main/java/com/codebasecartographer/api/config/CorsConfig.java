package com.codebasecartographer.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        
        // 1. SSE Endpoints Configuration
        registry.addMapping("/api/sse/**")
                .allowedOrigins(frontendUrl)
                .allowedMethods("GET")
                .allowedHeaders("Authorization", "Accept", "Cache-Control")
                .allowCredentials(false);
                
        // 2. All other /api/** Endpoints Configuration
        registry.addMapping("/api/**")
                // Spring evaluates mappings in order of specificity. Since /api/** encompasses
                // /api/sse/**, the SSE specific rules above take precedence for SSE paths.
                .allowedOrigins(frontendUrl)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Accept", "Cache-Control", "Content-Type")
                .allowCredentials(false);
    }
}
