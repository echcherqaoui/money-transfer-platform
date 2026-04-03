package com.moneytransfer.transaction.service;

import com.moneytransfer.transaction.dto.TransferRequest;
import com.moneytransfer.transaction.dto.TransferResponse;
import com.moneytransfer.transaction.enums.TransactionStatus;

import java.util.UUID;

public interface ITransactionService {
    TransferResponse initiateTransfer(TransferRequest request);

    TransferResponse getTransfer(UUID id);

    void updateStatus(UUID transactionId,
                      TransactionStatus newStatus);
}
