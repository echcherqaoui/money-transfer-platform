package com.moneytransfer.exception.handler;

import com.moneytransfer.exception.core.BaseCustomException;
import com.moneytransfer.exception.core.IErrorCode;
import com.moneytransfer.exception.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.moneytransfer.exception.core.CommonErrorCode.INTERNAL_SERVER_ERROR;
import static com.moneytransfer.exception.core.CommonErrorCode.VALIDATION_FAILED;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─── Shared validation response builder ───────────────────────
    private ResponseEntity<ErrorResponse> buildValidationErrorResponse(Set<String> errors,
                                                                       WebRequest request) {

        ErrorResponse response = new ErrorResponse(
              VALIDATION_FAILED,
              "Input validation failed",
              request.getDescription(false)
        ).setValidationErrors(errors);

        return ResponseEntity.badRequest().body(response);
    }

    // ─── Known business exceptions ────────────────────────────────
    // Catches all service-specific exceptions:
    // TransactionNotFoundException, InsufficientFundsException, etc.
    @ExceptionHandler(BaseCustomException.class)
    public ResponseEntity<ErrorResponse> handleBaseCustomException(BaseCustomException ex,
                                                                   WebRequest request) {

        IErrorCode errorCode = ex.getErrorCode() != null
              ? ex.getErrorCode()
              : INTERNAL_SERVER_ERROR;

        log.warn("Business exception [{}]: {}", errorCode.getCode(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
              errorCode,
              ex.getMessage(),
              request.getDescription(false)
        );

        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    // ─── @Valid on @RequestBody ────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleDtoValidation(MethodArgumentNotValidException ex,
                                                             WebRequest request) {

        Set<String> errors = ex.getBindingResult()
              .getFieldErrors()
              .stream()
              .map(e -> e.getField() + ": " + e.getDefaultMessage())
              .collect(Collectors.toSet());

        log.warn("DTO validation failed [{}]: {}", request.getDescription(false), errors);

        return buildValidationErrorResponse(errors, request);
    }

    // ─── @Validated on @PathVariable / @RequestParam ──────────────
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ConstraintViolationException ex,
                                                               WebRequest request) {

        Set<String> errors = ex.getConstraintViolations()
              .stream()
              .map(v -> v.getPropertyPath() + ": " + v.getMessage())
              .collect(Collectors.toSet());

        log.warn("Param validation failed [{}]: {}", request.getDescription(false), errors);

        return buildValidationErrorResponse(errors, request);
    }

    // ─── Catch-all ────────────────────────────────────────────────
    // Generates a unique error ID — client includes it in bug reports,
    // developer searches logs by that ID to find the exact stack trace
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex,
                                                       WebRequest request) {

        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception [ID: {}] on [{}]: {}",
              errorId,
              request.getDescription(false),
              ex.getMessage(),
              ex
        );

        ErrorResponse response = new ErrorResponse(
              INTERNAL_SERVER_ERROR,
              INTERNAL_SERVER_ERROR.formatMessage(errorId),
              request.getDescription(false)
        );

        return ResponseEntity.status(INTERNAL_SERVER_ERROR.getHttpStatus()).body(response);
    }
}