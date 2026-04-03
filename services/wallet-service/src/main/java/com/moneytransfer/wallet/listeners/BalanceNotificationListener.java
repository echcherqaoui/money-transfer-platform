package com.moneytransfer.wallet.listeners;

import com.moneytransfer.wallet.dto.BalanceUpdateEvent;
import com.moneytransfer.wallet.service.ISseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class BalanceNotificationListener {
    private final ISseEmitterService sseEmitterService;

    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleBalanceUpdate(BalanceUpdateEvent event) {
        sseEmitterService.pushBalanceUpdate(event.userId(), event.balance());
    }
}