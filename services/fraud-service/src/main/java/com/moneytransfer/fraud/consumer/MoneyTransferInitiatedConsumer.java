package com.moneytransfer.fraud.consumer;

import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.exception.core.EventSecurityException;
import com.moneytransfer.fraud.idempotency.IIdempotencyGuard;
import com.moneytransfer.fraud.service.IFraudDetectionService;
import com.moneytransfer.security.service.ISignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MoneyTransferInitiatedConsumer {
    private final IFraudDetectionService fraudDetectionService;
    private final ISignatureService signatureService;
    private final IIdempotencyGuard idempotencyGuard;

    private void verifySignature(MoneyTransferInitiated event) {
        boolean checkEventSignature = signatureService.verify(
              event.getEventId(),
              event.getTransactionId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!checkEventSignature) {
            log.error("Invalid HMAC signature on MoneyTransferInitiated: eventId={}", event.getEventId());
            throw new EventSecurityException();
        }
    }

    @KafkaListener(
          topics = "${kafka.topics.transfer-initiated}",
          containerFactory = "moneyTransferInitiatedListenerContainerFactory"
    )
    public void consume(MoneyTransferInitiated event, Acknowledgment ack) {
        log.debug(
              "Received MoneyTransferInitiated: transaction={} sender={}",
              event.getTransactionId(),
              event.getSenderId()
        );

        verifySignature(event); // throws EventSecurityException → DLT, no retry

        if (!idempotencyGuard.isFirstOccurrence(event.getEventId())) {
            log.warn("Duplicate event skipped: eventId={}", event.getEventId());
            ack.acknowledge();
            return;
        }

        fraudDetectionService.evaluate(event);

        ack.acknowledge();
    }
}