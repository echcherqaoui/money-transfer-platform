package com.moneytransfer.wallet.exception;

import com.moneytransfer.exception.core.BaseCustomException;
import com.moneytransfer.wallet.exception.enums.WalletErrorCode;

public class WalletException extends BaseCustomException {
    public WalletException(WalletErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}