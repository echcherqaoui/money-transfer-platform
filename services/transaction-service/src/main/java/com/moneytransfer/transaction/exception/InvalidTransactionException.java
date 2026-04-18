package com.moneytransfer.transaction.exception;

import com.moneytransfer.exception.core.BaseCustomException;
import com.moneytransfer.exception.core.IErrorCode;

import static com.moneytransfer.transaction.exception.enums.ErrorCode.SELF_TRANSFER_RESTRICTED;

public class InvalidTransactionException extends BaseCustomException {
    public InvalidTransactionException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public InvalidTransactionException() {
        super(SELF_TRANSFER_RESTRICTED);
    }
}
