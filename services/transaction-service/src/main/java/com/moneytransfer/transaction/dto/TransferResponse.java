package com.moneytransfer.transaction.dto;

import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.model.Transaction;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(UUID id,
                               UUID senderId,
                               UUID receiverId,
                               BigDecimal amount,
                               TransactionStatus status) {
    public static TransferResponse from(Transaction transaction){
        return new TransferResponse(
              transaction.getId(),
              transaction.getSenderId(),
              transaction.getReceiverId(),
              transaction.getAmount(),
              transaction.getStatus()
        );
    }
}