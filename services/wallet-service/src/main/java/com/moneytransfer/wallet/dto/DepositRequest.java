package com.moneytransfer.wallet.dto;

import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record DepositRequest(@Min(1) BigDecimal amount) {}