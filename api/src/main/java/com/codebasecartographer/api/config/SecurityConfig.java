package com.codebasecartographer.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

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
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Authentication required\"}");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http 
            .csrf(csrf -> csrf.disable()) // Disable CSRF for now
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .exceptionHandling(exc -> exc
                .authenticationEntryPoint(authenticationEntryPoint())
            )
            
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**") // All auth endpoints public
                .permitAll()
                .anyRequest().authenticated()
            )

            // Run JwtAuthFilter before Spring's default auth filter
            .addFilterBefore(jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
