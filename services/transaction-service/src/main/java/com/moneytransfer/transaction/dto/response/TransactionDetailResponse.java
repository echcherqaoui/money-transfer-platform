package com.moneytransfer.transaction.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.model.Transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionDetailResponse(UUID id,
                                        UUID senderId,
                                        UUID receiverId,
                                        BigDecimal amount,
                                        TransactionStatus status,
                                        OffsetDateTime createdAt,
                                        String reason) {
    public static TransactionDetailResponse from(Transaction transaction) {
        return new TransactionDetailResponse(
            transaction.getId(),
            transaction.getSenderId(),
            transaction.getReceiverId(),
            transaction.getAmount(),
            transaction.getStatus(),
            transaction.getCreatedAt(),
            transaction.getFailureReason() // The failure reason enum description or string
        );
    }
}