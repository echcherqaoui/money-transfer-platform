package com.moneytransfer.transaction.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
              .csrf(AbstractHttpConfigurer::disable)
              .sessionManagement(s -> s
                    .sessionCreationPolicy(STATELESS))
              .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/prometheus", "/actuator/health").permitAll()
                    .anyRequest().authenticated()
              )
              .oauth2ResourceServer(oauth2 -> oauth2
                    // Zero-trust — every service verifies for itself.
                    .jwt(jwt -> {
                    })
              )
              .build();
    }
}