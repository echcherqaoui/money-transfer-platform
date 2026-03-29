package com.moneytransfer.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceUpdateEvent(UUID userId, BigDecimal balance) {}