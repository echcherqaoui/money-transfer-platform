package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.contract.FraudDetected;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.contract.TransferApproved;
import com.moneytransfer.wallet.idempotency.IIdempotencyGuard;
import com.moneytransfer.wallet.service.ISettlementService;
import com.moneytransfer.wallet.service.ITransferOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.moneytransfer.wallet.enums.PendingStatus.DISCARDED;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferOrchestrator implements ITransferOrchestrator {

    private final IIdempotencyGuard idempotencyGuard;
    private final PendingTransferService pendingTransferService;
    private final ISettlementService settlementService;

    @Transactional
    @Override
    public void handleInitiated(MoneyTransferInitiated event) {
        if (idempotencyGuard.isProcessed(event.getEventId())){
            log.debug("Duplicate MoneyTransferInitiated skipped: eventId={}", event.getEventId());
            return;
        }

        pendingTransferService.store(
            UUID.fromString(event.getTransactionId()),
            UUID.fromString(event.getSenderId()),
            UUID.fromString(event.getReceiverId()),
            event.getAmountMinorUnits()
        );
    }

    @Transactional
    @Override
    public void handleApproved(TransferApproved event) {
        if (idempotencyGuard.isProcessed(event.getEventId())) {
            log.debug("Duplicate TransferApproved skipped: eventId={}", event.getEventId());
            return;
        }

        settlementService.settle(UUID.fromString(event.getTransactionId()));
    }

    @Transactional
    @Override
    public void handleFraud(FraudDetected event) {
        if (idempotencyGuard.isProcessed(event.getEventId())) {
            log.debug("Duplicate FraudDetected skipped: eventId={}", event.getEventId());
            return;
        }

        UUID transactionId = UUID.fromString(event.getTransactionId());

        int updatedCount = pendingTransferService.atomicStatusUpdate(transactionId, DISCARDED);

        if (updatedCount > 0)
            log.info("[DISCARDED] transaction={} reason=fraud_detected", transactionId);
        else
            log.warn("[SKIP] transaction={} could not be DISCARDED. Current state may have changed.", transactionId);
    }
}