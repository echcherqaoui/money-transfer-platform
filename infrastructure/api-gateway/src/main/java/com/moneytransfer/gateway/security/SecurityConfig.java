package com.moneytransfer.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;

/**
 * Security configuration for the API Gateway (BFF).
 * * Acts as an OAuth2 Client to manage user authentication via Keycloak.
 * Handles the full authorization code flow, session synchronization via Redis,
 * and global logout propagation to ensure session consistency across the platform.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final ReactiveClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(ReactiveClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
              .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
              ).authorizeExchange(exchanges -> exchanges
                    .pathMatchers("/actuator/health", "/login**", "/oauth2/**").permitAll()
                    .pathMatchers("/actuator/**").hasRole("ACTUATOR")
                    .anyExchange().authenticated()
              )
              .oauth2Login(Customizer.withDefaults())
              .logout(logout -> logout
                    .logoutSuccessHandler(oidcLogoutSuccessHandler())
              )
              .build();
    }

    // Propagates logout to Keycloak — invalidates the Keycloak session too
    private ServerLogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedServerLogoutSuccessHandler handler =
              new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}");
        return handler;
    }
}