package com.moneytransfer.wallet.service;

import com.google.protobuf.Timestamp;

import java.util.UUID;

public interface ISettlementService {
    void settle(UUID transactionId, Timestamp expiresAt);
}