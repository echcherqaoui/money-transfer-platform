package com.moneytransfer.fraud.service.impl;

import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.fraud.properties.FraudProperties;
import com.moneytransfer.fraud.service.IFraudDetectionService;
import com.moneytransfer.fraud.service.IFraudEventProducer;
import com.moneytransfer.fraud.velocity.IVelocityTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionServiceImpl implements IFraudDetectionService {

    private final FraudProperties fraudProperties;
    private final IVelocityTracker velocityTracker;
    private final IFraudEventProducer fraudEventProducer;

    private String checkAmountThreshold(MoneyTransferInitiated event, long threshold) {
        if (event.getAmountMinorUnits() > threshold)
            return String.format(
                  "Amount %d exceeds threshold of %d minor units",
                  event.getAmountMinorUnits(),
                  threshold
            );

        return null;
    }

    private String checkVelocity(String senderId,
                                 String eventId) {
        int maxTransactions = fraudProperties.getVelocity().getMaxTransactions();
        int windowMinutes = fraudProperties.getVelocity().getWindowMinutes();

        if (velocityTracker.recordAndCheckVelocity(senderId, eventId, maxTransactions, windowMinutes))
            return String.format(
                  "Sender %s exceeded %d transfers within %d-minute window",
                  senderId,
                  maxTransactions,
                  windowMinutes
            );

        return null;
    }

    private String detectFraud(MoneyTransferInitiated event, String senderId) {
        long threshold = fraudProperties.getAmountThresholdMinorUnits();

        String amountViolation = checkAmountThreshold(event, threshold);

        return amountViolation != null?
              amountViolation:
              checkVelocity(senderId, event.getEventId());
    }

    // Evaluates the event against all fraud rules and publishes the outcome.
    @Override
    public void evaluate(MoneyTransferInitiated event) {
        String transactionId = event.getTransactionId();
        String senderId = event.getSenderId();

        String fraudReason = detectFraud(event, senderId);

        if (fraudReason != null) {
            log.warn(
                  "[FRAUD] transaction={} sender={} reason={}",
                  transactionId,
                  senderId,
                  fraudReason
            );

            fraudEventProducer.publishFraudDetected(transactionId, senderId, fraudReason);
        } else {

            log.info(
                  "[APPROVED] transaction={} sender={}",
                  transactionId,
                  senderId
            );

            fraudEventProducer.publishTransferApproved(transactionId);
        }
    }
}