package com.moneytransfer.transaction.service;

import com.moneytransfer.transaction.dto.request.TransferRequest;
import com.moneytransfer.transaction.dto.response.PaginatedResponse;
import com.moneytransfer.transaction.dto.response.TransactionDetailResponse;
import com.moneytransfer.transaction.dto.response.TransactionResponse;
import com.moneytransfer.transaction.dto.response.TransactionStatsResponse;
import com.moneytransfer.transaction.enums.TransactionStatus;

import java.util.UUID;

public interface ITransactionService {
    TransactionResponse initiateTransfer(TransferRequest request);

    PaginatedResponse<TransactionResponse> getTransfers(int page,
                                                        int size,
                                                        TransactionStatus status);

    TransactionDetailResponse getTransfer(UUID id);

    TransactionStatsResponse getStats();

    void updateStatus(UUID transactionId,
                      UUID senderId,
                      UUID receiverId,
                      String reason,
                      TransactionStatus newStatus);
}
