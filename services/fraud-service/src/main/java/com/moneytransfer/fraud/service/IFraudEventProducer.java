package com.moneytransfer.fraud.service;

import com.google.protobuf.Timestamp;

public interface IFraudEventProducer {
    void publishFraudDetected(String transactionId,
                              String senderId,
                              String reason);

    void publishTransferApproved(String transactionId, Timestamp expiresAt);
}
