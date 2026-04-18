package com.moneytransfer.transaction.dto.response;

import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.model.Transaction;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionResponse(UUID id,
                                  UUID senderId,
                                  UUID receiverId,
                                  BigDecimal amount,
                                  TransactionStatus status) {
    public static TransactionResponse from(Transaction transaction){
        return new TransactionResponse(
              transaction.getId(),
              transaction.getSenderId(),
              transaction.getReceiverId(),
              transaction.getAmount(),
              transaction.getStatus()
        );
    }
}