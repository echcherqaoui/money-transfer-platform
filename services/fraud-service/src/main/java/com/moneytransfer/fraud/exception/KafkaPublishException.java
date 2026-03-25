package com.moneytransfer.fraud.exception;


import com.moneytransfer.exception.core.BaseCustomException;
import com.moneytransfer.fraud.exception.enums.FraudErrorCode;

public class KafkaPublishException extends BaseCustomException {

    public KafkaPublishException(FraudErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}