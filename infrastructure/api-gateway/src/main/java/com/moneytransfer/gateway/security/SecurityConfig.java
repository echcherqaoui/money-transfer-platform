package com.moneytransfer.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

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

    // Propagates logout to Keycloak — invalidates the Keycloak session too
    private ServerLogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedServerLogoutSuccessHandler logoutHandler =
              new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        logoutHandler.setPostLogoutRedirectUri("{baseUrl}/logged-out");
        return logoutHandler;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
              .headers(headers -> headers
                    .cache(ServerHttpSecurity.HeaderSpec.CacheSpec::disable) // Critical for logout security
              )
              .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
              ).authorizeExchange(exchanges -> exchanges
                    .pathMatchers(
                          "/actuator/prometheus",
                          "/actuator/health",
                          "/login**",
                          "/oauth2/**",
                          "/debug/**",
                          "/logged-out"
                    ).permitAll()
                    .anyExchange().authenticated()
              ).exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((exchange, ex) -> {
                        // If the request expects JSON → return 401
                        // If the request expects HTML → redirect to Keycloak
                        if (exchange.getRequest().getHeaders().getAccept().stream()
                              .anyMatch(mediaType -> mediaType.includes(APPLICATION_JSON))) {
                            exchange.getResponse().setStatusCode(UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                        // Otherwise, redirect to Keycloak (default behavior)
                        return new RedirectServerAuthenticationEntryPoint("/oauth2/authorization/keycloak")
                              .commence(exchange, ex);
                    })
              ).oauth2Login(Customizer.withDefaults())
              .logout(logout -> logout
                    .logoutSuccessHandler(oidcLogoutSuccessHandler())
              ).build();
    }

    @Bean
    public WebFilter csrfCookieWebFilter() {
        return (exchange, chain) -> exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty())
              .doOnSuccess(token -> {
                  // This line "activates" the token so it's sent to frontend
              }).then(chain.filter(exchange));
    }
}