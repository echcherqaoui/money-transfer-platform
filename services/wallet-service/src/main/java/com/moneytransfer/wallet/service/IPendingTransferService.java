package com.moneytransfer.wallet.service;

import com.moneytransfer.wallet.enums.PendingStatus;
import com.moneytransfer.wallet.model.PendingTransfer;

import java.math.BigDecimal;
import java.util.UUID;

public interface IPendingTransferService {
    void storeAs(UUID transactionId,
                 UUID senderId,
                 UUID receiverId,
                 BigDecimal amount,
                 PendingStatus status);

    PendingTransfer getPendingTransfer(UUID transactionId);

    void updateStatus(PendingTransfer pending,
                      PendingStatus status);

    int atomicStatusUpdate(UUID transactionId,
                           PendingStatus newStatus);
}