package com.moneytransfer.transaction.exception;

import com.moneytransfer.exception.core.BaseCustomException;
import com.moneytransfer.exception.core.IErrorCode;

import java.util.UUID;

import static com.moneytransfer.transaction.exception.enums.ErrorCode.TRANSACTION_NOT_FOUND;

public class TransactionNotFoundException extends BaseCustomException {
    public TransactionNotFoundException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public TransactionNotFoundException(UUID transactionId) {
        super(TRANSACTION_NOT_FOUND, transactionId);
    }
}
