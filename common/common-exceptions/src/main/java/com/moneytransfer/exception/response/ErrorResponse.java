package com.moneytransfer.exception.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moneytransfer.exception.core.IErrorCode;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {
    private final String code;
    private final String message;
    private final int httpStatus;
    private final OffsetDateTime timestamp;
    private final String path;

    // Only populated for validation errors — @JsonInclude.NON_EMPTY hides it otherwise
    private Set<String> validationErrors;

    /**
     * For known IErrorCode exceptions — code, status, message from the error code.
     */
    public ErrorResponse(IErrorCode errorCode,
                         String message,
                         String path) {
        this.code = errorCode.getCode();
        this.message = message;
        this.httpStatus = errorCode.getHttpStatus();
        this.timestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        this.path = sanitizePath(path);
    }

    /**
     * For unexpected/generic exceptions — no IErrorCode available.
     */
    public ErrorResponse(String code,
                         String message,
                         int httpStatus,
                         String path) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.timestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        this.path = sanitizePath(path);
    }

    // Spring prefixes paths with "uri=" internally — strip it for clean API responses
    private String sanitizePath(String path) {
        return path != null ? path.replace("uri=", "") : null;
    }

    public ErrorResponse setValidationErrors(Set<String> validationErrors) {
        this.validationErrors = validationErrors;
        return this;
    }
}