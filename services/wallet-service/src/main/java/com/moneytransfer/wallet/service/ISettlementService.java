package com.moneytransfer.wallet.service;

import java.util.UUID;

public interface ISettlementService {
    void settle(UUID transactionId);
}