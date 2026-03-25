package com.moneytransfer.fraud.service;

public interface IFraudEventProducer {
    //Publishes FraudDetected.
    void publishFraudDetected(String transactionId,
                              String senderId,
                              String reason);

    //Publishes TransferApproved.
    void publishTransferApproved(String transactionId);
}
