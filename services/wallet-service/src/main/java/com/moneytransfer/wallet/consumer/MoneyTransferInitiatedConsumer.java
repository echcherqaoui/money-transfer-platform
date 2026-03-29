package com.moneytransfer.wallet.consumer;

import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.exception.core.EventSecurityException;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.wallet.service.ITransferOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MoneyTransferInitiatedConsumer {
    private final ISignatureService signatureService;
    private final ITransferOrchestrator transferOrchestrator;

    private void verifySignatureOrFail(MoneyTransferInitiated event) {
        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getTransactionId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );
        if (!valid) {
            log.error("Invalid HMAC signature on MoneyTransferInitiated: eventId={}", event.getEventId());
            throw new EventSecurityException();
        }
    }

    @KafkaListener(
          topics = "${kafka.topics.transaction.transfer-initiated}",
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "moneyTransferInitiatedListenerContainerFactory"
    )
    public void consume(MoneyTransferInitiated event, Acknowledgment ack) {
        log.debug("Received MoneyTransferInitiated: transaction={}", event.getTransactionId());

        verifySignatureOrFail(event);

        transferOrchestrator.handleInitiated(event);

        ack.acknowledge();
    }
}