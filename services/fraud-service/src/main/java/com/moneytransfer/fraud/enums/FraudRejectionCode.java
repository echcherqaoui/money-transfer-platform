package com.moneytransfer.fraud.enums;

public enum FraudRejectionCode {
    AMOUNT_THRESHOLD_EXCEEDED("The transaction amount exceeds the maximum allowed limit for a single transfer"),
    VELOCITY_LIMIT_EXCEEDED("Too many transactions performed within a short period"),
    SUSPICIOUS_ACTIVITY("The transaction matches a known pattern of fraudulent behavior");

    private final String description;

    FraudRejectionCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}