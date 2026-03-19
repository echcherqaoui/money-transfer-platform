package com.moneytransfer.exception.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Error codes shared across all services.
 * Each service defines its own enum implementing IErrorCode for domain-specific codes.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements IErrorCode {

    VALIDATION_FAILED("REQ_400", "Input validation failed", 400),
    INTERNAL_SERVER_ERROR("GEN_500", "An unexpected error occurred [ID: %s]", 500),
    UNAUTHORIZED("AUTH_401", "Authentication required", 401),
    INVALID_SIGNATURE("SEC_403", "Security violation: Invalid HMAC signature.", 403),
    FORBIDDEN("GEN_403", "Access denied", 403);

    private final String code;
    private final String message;
    private final int httpStatus;
}