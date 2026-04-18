package com.moneytransfer.gateway.dto;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record UserResponse(String id,
                           String username,
                           String email,
                           List<String> roles) {
    public static UserResponse fromOidcUser(OidcUser user) {
        List<String> extractedRoles = Collections.emptyList();

        // Safely check if "realm_access" exists AND is a Map && Safely check if "roles" exists inside that map AND is a List
        if (user.getClaim("realm_access") instanceof Map<?, ?> realmAccess && realmAccess.get("roles") instanceof List<?> list)
            extractedRoles = list.stream()
                  .filter(String.class::isInstance)
                  .map(String.class::cast)
                  .toList();

        return new UserResponse(
              user.getSubject(),
              user.getPreferredUsername(),
              user.getEmail(),
              extractedRoles
        );
    }
}