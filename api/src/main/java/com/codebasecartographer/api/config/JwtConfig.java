package com.codebasecartographer.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// @ConfigurationProperties reads from application.yaml
// prefix = "jwt" means it reads jwt.secret and jwt.expiration
@Configuration
@ConfigurationProperties(prefix="jwt")
public class JwtConfig {
    //Maps to jwt.secret in application.yaml
    private String secret;

    //Maps to jwt.expiration in same application.yaml
    private long expiration;

    // Getters and Setters required by Spring to inject values
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getExpiration() { return expiration; }
    public void setExpiration( long expiration) { this.expiration = expiration; }
}
