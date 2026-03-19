package com.moneytransfer.security.jwt;

import java.util.UUID;

public record AuthenticatedUser(UUID userId,
                                String email) {}
