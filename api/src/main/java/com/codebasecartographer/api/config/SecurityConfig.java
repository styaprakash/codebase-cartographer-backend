package com.codebasecartographer.api.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.codebasecartographer.api.filter.JwtAuthFilter;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
            log.warn("Authentication required for {} {}", request.getMethod(), request.getRequestURI());
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Authentication required\"}");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http 
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .csrf(csrf -> csrf.disable()) // Disable CSRF for now
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .exceptionHandling(exc -> exc
                .authenticationEntryPoint(authenticationEntryPoint())
            )
            
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll() // All auth endpoints public
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll() // Allow error dispatches (SSE disconnects)
                .requestMatchers("/error").permitAll()       // Let Spring handle errors gracefully
                .anyRequest().authenticated()
            )

            // Run JwtAuthFilter before Spring's default auth filter
            .addFilterBefore(jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
