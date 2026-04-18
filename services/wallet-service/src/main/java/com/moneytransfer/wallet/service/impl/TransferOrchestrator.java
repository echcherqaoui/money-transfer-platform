package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.contract.FraudDetected;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.contract.TransferApproved;
import com.moneytransfer.wallet.enums.PendingStatus;
import com.moneytransfer.wallet.idempotency.IIdempotencyGuard;
import com.moneytransfer.wallet.service.ISettlementService;
import com.moneytransfer.wallet.service.ITransferOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.moneytransfer.wallet.enums.PendingStatus.DISCARDED;
import static com.moneytransfer.wallet.enums.PendingStatus.EXPIRED;
import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferOrchestrator implements ITransferOrchestrator {

    private final IIdempotencyGuard idempotencyGuard;
    private final PendingTransferService pendingTransferService;
    private final ISettlementService settlementService;

    @NotNull
    private UUID getUuid(String value) {
        return UUID.fromString(value);
    }

    @Transactional
    @Override
    public void handleInitiated(MoneyTransferInitiated event) {
        if (idempotencyGuard.isProcessed(event.getEventId())) {
            log.debug("Duplicate MoneyTransferInitiated skipped: eventId={}", event.getEventId());
            return;
        }

        Instant expiresAt = Instant.ofEpochSecond(
              event.getExpiresAt().getSeconds(),
              event.getExpiresAt().getNanos()
        );

        PendingStatus status = Instant.now().isAfter(expiresAt)? EXPIRED: INITIATED;

        BigDecimal amount = BigDecimal.valueOf(event.getAmountMinorUnits()).movePointLeft(4);

        pendingTransferService.storeAs(
              getUuid(event.getTransactionId()),
              getUuid(event.getSenderId()),
              getUuid(event.getReceiverId()),
              amount,
              status
        );

        if (status == EXPIRED)
            log.warn(
                  "[EXPIRED-ON-ARRIVAL] transaction={} amount={} - Stored to block late settlements.",
                  event.getTransactionId(),
                  amount
            );
        else
            log.info(
                  "[PENDING-STORED] transaction={} sender={} amount={}",
                  event.getTransactionId(),
                  event.getSenderId(),
                  amount
            );
    }

    @Transactional
    @Override
    public void handleApproved(TransferApproved event) {
        if (idempotencyGuard.isProcessed(event.getEventId())) {
            log.debug("Duplicate TransferApproved skipped: eventId={}", event.getEventId());
            return;
        }

        settlementService.settle(getUuid(event.getTransactionId()), event.getExpiresAt());
    }

    @Transactional
    @Override
    public void handleFraud(FraudDetected event) {
        if (idempotencyGuard.isProcessed(event.getEventId())) {
            log.debug("Duplicate FraudDetected skipped: eventId={}", event.getEventId());
            return;
        }

        UUID transactionId = getUuid(event.getTransactionId());

        int updatedCount = pendingTransferService.atomicStatusUpdate(transactionId, DISCARDED);

        if (updatedCount > 0)
            log.info("[DISCARDED] transaction={} reason=fraud_detected", transactionId);
        else
            log.warn("[SKIP] transaction={} could not be DISCARDED. Current state may have changed.", transactionId);
    }
}