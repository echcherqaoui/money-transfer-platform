package com.moneytransfer.transaction.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(@NotNull(message = "receiverId is required") UUID receiverId,
                              @NotNull(message = "amountMinorUnits is required")
                              @Min(value = 1, message = "Amount must be at least 1 unit") BigDecimal amount) {}
 