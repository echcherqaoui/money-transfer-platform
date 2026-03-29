package com.moneytransfer.wallet.exception.enums;

import com.moneytransfer.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalletErrorCode implements IErrorCode {

    WALLET_NOT_FOUND("WALLET_404", "Wallet not found for user: %s", 404),
    PENDING_TRANSFER_NOT_FOUND("WALLET_404_T", "No pending transfer found for transaction: %s", 404),

    WALLET_ALREADY_EXISTS("WALLET_409", "Wallet already exists for user: %s", 409),

    INSUFFICIENT_FUNDS("WALLET_422", "Insufficient funds for sender: %s", 422),
    DEPOSIT_LIMIT_EXCEEDED("WALLET_422_D", "Deposit amount exceeds single limit of %s minor units", 422),
    DAILY_DEPOSIT_LIMIT_EXCEEDED("WALLET_422_DD", "Daily deposit limit of %s minor units exceeded", 422),
    WALLET_PARTICIPANTS_MISSING("WALLET_500_STATE", "Required sender or receiver wallet missing for IDs: %s", 500),
    KAFKA_PUBLISH_FAILURE("WALLET_500", "Failed to publish event to topic: %s", 500),
    KAFKA_PUBLISH_INTERRUPTED("WALLET_500_I", "Kafka publish interrupted for topic: %s", 500),
    KAFKA_PUBLISH_TIMEOUT("WALLET_504", "Timed out waiting for broker acknowledgment for topic: %s", 504);



    private final String code;
    private final String message;
    private final int httpStatus;
}