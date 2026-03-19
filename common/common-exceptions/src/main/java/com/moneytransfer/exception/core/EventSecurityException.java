package com.moneytransfer.exception.core;

import static com.moneytransfer.exception.core.CommonErrorCode.INVALID_SIGNATURE;

public class EventSecurityException extends BaseCustomException {
    public EventSecurityException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
    
    public EventSecurityException() {
        super(INVALID_SIGNATURE);
    }
}