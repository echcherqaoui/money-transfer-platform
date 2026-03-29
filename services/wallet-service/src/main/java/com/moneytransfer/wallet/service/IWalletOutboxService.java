package com.moneytransfer.wallet.service;

import java.util.UUID;

public interface IWalletOutboxService {
    void publishTransferCompleted(UUID transactionId,
                                  UUID senderId,
                                  UUID receiverId,
                                  long amountMinorUnits,
                                  long senderNewBalance,
                                  long receiverNewBalance);

    void publishTransferFailed(UUID transactionId,
                               UUID senderId,
                               String reason);
}