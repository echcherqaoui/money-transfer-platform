package com.moneytransfer.transaction.exception;

import com.moneytransfer.exception.core.BaseCustomException;
import com.moneytransfer.exception.core.IErrorCode;

import java.util.UUID;

import static com.moneytransfer.transaction.exception.enums.ErrorCode.TRANSACTION_STATE_CONFLICT;

public class ConcurrentUpdateException extends BaseCustomException {
    public ConcurrentUpdateException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ConcurrentUpdateException(UUID transactionId) {
        super(TRANSACTION_STATE_CONFLICT, transactionId);
    }
}
