package com.moneytransfer.transaction.dto.response;

import java.math.BigDecimal;

public record TransactionStatsResponse(BigDecimal totalSent,
                                       BigDecimal totalReceived,
                                       Long totalCount){ }