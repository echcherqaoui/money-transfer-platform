package com.moneytransfer.wallet.service;

import com.moneytransfer.wallet.enums.PendingStatus;
import com.moneytransfer.wallet.model.PendingTransfer;

import java.util.UUID;

public interface IPendingTransferService {
    void store(UUID transactionId, UUID senderId, UUID receiverId, long amountMinorUnits);

    PendingTransfer getPendingTransfer(UUID transactionId);

    void updateStatus(PendingTransfer pending, PendingStatus status);

    int atomicStatusUpdate(UUID transactionId, PendingStatus newStatus);
}