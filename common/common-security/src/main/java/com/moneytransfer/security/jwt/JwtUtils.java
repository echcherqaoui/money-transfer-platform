package com.moneytransfer.security.jwt;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Static utility for extracting user information from a Keycloak JWT.
 */
public final class JwtUtils {

    private JwtUtils() {}

    private static Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AuthenticationCredentialsNotFoundException("No valid JWT found in SecurityContext");
    }

    private static UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static String extractEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null) {
            throw new IllegalStateException("Email claim missing from JWT");
        }
        return email;
    }

    public static AuthenticatedUser extractUser() {
        Jwt jwt = getJwt();
        return new AuthenticatedUser(extractUserId(jwt), extractEmail(jwt));
    }

    public static UUID extractUserId() {
        return extractUserId(getJwt());
    }

    public static String extractEmail() {
        return extractEmail(getJwt());
    }
}