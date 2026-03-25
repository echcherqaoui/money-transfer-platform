package com.moneytransfer.fraud.exception.enums;

import com.moneytransfer.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FraudErrorCode implements IErrorCode {

    KAFKA_PUBLISH_FAILURE("KAFKA_500_ERR", "Failed to publish event to topic:  %s", 500),
    KAFKA_PUBLISH_INTERRUPTED("KAFKA_500_INT", "Kafka publish interrupted for topic: %s", 500),

    KAFKA_PUBLISH_TIMEOUT("KAFKA_504", "Timed out waiting for broker acknowledgment for topic %s", 504);

    private final String code;
    private final String message;
    private final int httpStatus;
}