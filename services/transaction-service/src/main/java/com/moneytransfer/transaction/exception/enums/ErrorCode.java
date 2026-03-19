package com.moneytransfer.transaction.exception.enums;

import com.moneytransfer.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements IErrorCode {
    TRANSACTION_NOT_FOUND("TRANSACTION_404", "transaction with ID %s not found", 404);

    private final String code;
    private final String message;
    private final int httpStatus;
}
