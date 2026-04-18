package com.moneytransfer.fraud.service.impl;

import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.fraud.properties.FraudProperties;
import com.moneytransfer.fraud.service.IFraudDetectionService;
import com.moneytransfer.fraud.service.IFraudEventProducer;
import com.moneytransfer.fraud.velocity.IVelocityTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static com.moneytransfer.fraud.enums.FraudRejectionCode.AMOUNT_THRESHOLD_EXCEEDED;
import static com.moneytransfer.fraud.enums.FraudRejectionCode.VELOCITY_LIMIT_EXCEEDED;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionServiceImpl implements IFraudDetectionService {

    private final FraudProperties fraudProperties;
    private final IVelocityTracker velocityTracker;
    private final IFraudEventProducer fraudEventProducer;

    private String checkAmountThreshold(MoneyTransferInitiated event, long threshold) {
        if (event.getAmountMinorUnits() > threshold)
            return AMOUNT_THRESHOLD_EXCEEDED.getDescription();

        return null;
    }

    private String checkVelocity(String senderId,
                                             String eventId) {
        int maxTransactions = fraudProperties.getVelocity().getMaxTransactions();
        int windowMinutes = fraudProperties.getVelocity().getWindowMinutes();

        boolean isExceeded = velocityTracker.recordAndCheckVelocity(
              senderId,
              eventId,
              maxTransactions,
              windowMinutes
        );

        return isExceeded ? VELOCITY_LIMIT_EXCEEDED.getDescription() : null;
    }

    private String detectFraud(MoneyTransferInitiated event, String senderId) {
        long threshold = fraudProperties.getAmountThresholdMinorUnits();

        String amountViolation = checkAmountThreshold(event, threshold);

        return amountViolation != null ?
              amountViolation :
              checkVelocity(senderId, event.getEventId());
    }

    // Evaluates the event against all fraud rules and publishes the outcome.
    @Override
    public void evaluate(MoneyTransferInitiated event) {
        String transactionId = event.getTransactionId();
        String senderId = event.getSenderId();

        Instant expiresAt = Instant.ofEpochSecond(
              event.getExpiresAt().getSeconds(),
              event.getExpiresAt().getNanos()
        );

        if (Instant.now().isAfter(expiresAt)) {
            log.warn(
                  "[EXPIRED-ON-ARRIVAL] transaction={} — publishing FraudDetected to close wallet pending",
                  transactionId
            );
            // No velocity recorded
            return;
        }

        String fraudReason = detectFraud(event, senderId);

        if (fraudReason != null) {
            log.warn(
                  "[FRAUD] transaction={} sender={} reason={}",
                  transactionId,
                  senderId,
                  fraudReason
            );

            fraudEventProducer.publishFraudDetected(
                  transactionId,
                  senderId,
                  fraudReason
            );
        } else {
            log.info(
                  "[APPROVED] transaction={} sender={}",
                  transactionId,
                  senderId
            );

            fraudEventProducer.publishTransferApproved(transactionId, event.getExpiresAt());
        }
    }
}