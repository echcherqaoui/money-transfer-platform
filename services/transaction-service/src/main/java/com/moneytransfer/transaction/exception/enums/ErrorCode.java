package com.moneytransfer.transaction.exception.enums;

import com.moneytransfer.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements IErrorCode {
    SELF_TRANSFER_RESTRICTED("TRANSACTION_400", "Cannot perform a transfer to your own account", 400),
    TRANSFER_ACCESS_DENIED("TRANSACTION_403", "You do not have permission to access this transfer", 403),
    TRANSACTION_NOT_FOUND("TRANSACTION_404", "transaction with ID %s not found", 404),
    TRANSACTION_STATE_CONFLICT("TRANSACTION_409", "transaction %s is no longer in the expected state (PENDING)", 409);
    private final String code;
    private final String message;
    private final int httpStatus;
}
