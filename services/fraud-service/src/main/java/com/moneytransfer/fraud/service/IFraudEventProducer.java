package com.moneytransfer.fraud.service;

public interface IFraudEventProducer {
    void publishFraudDetected(String transactionId,
                              String senderId,
                              String reason);

    void publishTransferApproved(String transactionId);
}
