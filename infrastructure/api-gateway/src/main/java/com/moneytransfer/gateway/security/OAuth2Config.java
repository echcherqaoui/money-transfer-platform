package com.moneytransfer.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;

import java.util.Map;

import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;
import static org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC;

@Configuration
public class OAuth2Config {

    @Bean
    public ReactiveClientRegistrationRepository clientRegistrationRepository(@Value("${KC_PUBLIC_BASE_URL}") String publicUrl,
                                                                             @Value("${KC_INTERNAL_BASE_URL}") String internalUrl,
                                                                             @Value("${KC_REALM}") String realm,
                                                                             @Value("${KC_CLIENT_ID}") String clientId,
                                                                             @Value("${KC_CLIENT_SECRET}") String clientSecret) {

        String realmPath = "/realms/" + realm + "/protocol/openid-connect";

        ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
              .clientId(clientId)
              .clientSecret(clientSecret)
              .clientAuthenticationMethod(CLIENT_SECRET_BASIC)
              .authorizationGrantType(AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/keycloak")
              .scope("openid", "profile", "email")

              // --- BROWSER-FACING ENDPOINTS ---
              // These must be accessible by the user's browser
              .authorizationUri(publicUrl + realmPath + "/auth")

              // --- SERVER-TO-SERVER ENDPOINTS ---
              // The Gateway uses these to talk to Keycloak directly
              .tokenUri(internalUrl + realmPath + "/token")
              .jwkSetUri(internalUrl + realmPath + "/certs")
              .userInfoUri(internalUrl + realmPath + "/userinfo")

              // --- LOGOUT METADATA ---
              // Required for Logout (browser-facing)
              .providerConfigurationMetadata(Map.of("end_session_endpoint", publicUrl + realmPath + "/logout"))

              .userNameAttributeName("sub")
              .clientName("Keycloak")
              .build();

        return new InMemoryReactiveClientRegistrationRepository(registration);
    }
}