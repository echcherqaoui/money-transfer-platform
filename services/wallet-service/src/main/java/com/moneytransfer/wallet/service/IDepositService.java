package com.moneytransfer.wallet.service;

import com.moneytransfer.wallet.dto.WalletResponse;

import java.math.BigDecimal;
import java.util.UUID;

public interface IDepositService {
    WalletResponse deposit(UUID userId, BigDecimal amount);
}