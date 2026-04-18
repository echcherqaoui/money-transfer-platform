package com.moneytransfer.gateway.controller;

import com.moneytransfer.gateway.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.base-path}/auth")
public class AuthController {
    @GetMapping("/me")
    public Mono<UserResponse> getUser(@AuthenticationPrincipal OidcUser oidcUser) {
        return Mono.just(
              UserResponse.fromOidcUser(oidcUser)
        );
    }
}

