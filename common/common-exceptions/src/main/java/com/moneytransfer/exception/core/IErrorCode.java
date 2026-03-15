package com.moneytransfer.exception.core;

public interface IErrorCode {

    String getCode();
    String getMessage();
    int getHttpStatus();

    /**
     * Default implementation using String.format.
     * Enums get this — Override only if custom formatting is needed.
     */
    default String formatMessage(Object... args) {
        return args.length > 0
                ? String.format(getMessage(), args)
                : getMessage();
    }
}