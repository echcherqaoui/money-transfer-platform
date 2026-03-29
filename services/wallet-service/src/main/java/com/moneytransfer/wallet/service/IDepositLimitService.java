package com.moneytransfer.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface IDepositLimitService {
    void validate(UUID userId, BigDecimal amount);
}
