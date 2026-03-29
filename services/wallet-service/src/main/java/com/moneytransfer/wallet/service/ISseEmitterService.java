package com.moneytransfer.wallet.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.UUID;

public interface ISseEmitterService {
    SseEmitter register();

    void pushBalanceUpdate(UUID userId, BigDecimal balance);
}
