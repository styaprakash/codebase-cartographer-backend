package com.codebasecartographer.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.codebasecartographer.api.filter.JwtAuthFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    //dependency
    private final JwtAuthFilter jwtAuthFilter;

    //constructor injection
    public SecurityConfig(JwtAuthFilter jwtAuthFilter){
        this.jwtAuthFilter = jwtAuthFilter;
    }

    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http 
            .csrf(csrf -> csrf.disable()) // Disable CSRF for now
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**") // GitHub OAuth callback
                .authenticated()
                .anyRequest().permitAll() // Allow all requests (no auth for now)
            )

            // Run JwtAuthFilter before Spring's default auth filter
            .addFilterBefore(jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
